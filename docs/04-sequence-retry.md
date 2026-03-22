# Sequence Diagram — Retry Flow

Detailed sequence for `POST /account-posting/retry`. Highlights the atomic lock mechanism, parallel `CompletableFuture` dispatch, and per-posting retry processing.

---

## Retry Flow — Top Level (Lock + Dispatch)

```mermaid
sequenceDiagram
    autonumber

    participant Client as Upstream Caller / UI
    participant MDC as MdcLoggingFilter
    participant Ctrl as AccountPostingController
    participant Svc as AccountPostingService
    participant PostRepo as AccountPostingRepository
    participant Executor as retryExecutor<br/>(Thread Pool)
    participant Processor as PostingRetryProcessor
    participant LegSvc as AccountPostingLegService
    participant LegRepo as AccountPostingLegRepository
    participant Factory as PostingStrategyFactory
    participant Strategy as Strategy Impl<br/>(CBS/GL/OBPM)
    participant Ext as External System Stub
    participant Publisher as PostingEventPublisher
    participant Kafka as Kafka

    Client->>MDC: POST /account-posting/retry<br/>{ postingIds: [42, 43] }  OR  { postingIds: [] }
    MDC->>MDC: Seed MDC traceId
    MDC->>Ctrl: Forward

    Ctrl->>Svc: retryPostings(RetryRequest)

    Note over Svc: Step 1 — Resolve target postings
    alt postingIds provided in request
        Svc->>PostRepo: findAllById(postingIds)
        PostRepo-->>Svc: List<AccountPosting> (may include non-PENDING)
    else no postingIds — retry all non-SUCCESS
        Svc->>PostRepo: findByStatusNot(SUCCESS)
        PostRepo-->>Svc: List<AccountPosting> [42(PENDING), 43(PENDING)]
    end

    Note over Svc: Step 2 — Atomic lock via single UPDATE
    Svc->>PostRepo: lockForRetry(postingIds, lockedUntil = NOW() + 2min)
    Note right of PostRepo: Single @Modifying JPQL:<br/>UPDATE account_posting<br/>SET retry_locked_until = :until<br/>WHERE posting_id IN :ids<br/>AND (retry_locked_until IS NULL<br/>  OR retry_locked_until < NOW())
    PostRepo-->>Svc: updatedCount (number actually locked)

    Svc->>PostRepo: findLockedForRetry(postingIds, NOW())
    PostRepo-->>Svc: List<AccountPosting> actually locked (skip already-locked)

    Note over Svc: Step 3 — Dispatch one CompletableFuture per locked posting
    loop for each locked posting
        Svc->>Executor: CompletableFuture.supplyAsync(<br/>  () -> processor.process(posting), retryExecutor)
    end

    Note over Svc: Step 4 — Wait for all futures
    Svc->>Svc: CompletableFuture.allOf(futures).join()

    Svc->>Svc: Collect results into RetryResponse
    Svc-->>Ctrl: RetryResponse { attempted: 2, succeeded: 1, failed: 1, skipped: 0 }
    Ctrl-->>Client: 200 OK { data: RetryResponse }
```

---

## Retry Flow — Per-Posting Processing (PostingRetryProcessor)

```mermaid
sequenceDiagram
    autonumber

    participant Processor as PostingRetryProcessor
    participant PostRepo as AccountPostingRepository
    participant LegRepo as AccountPostingLegRepository
    participant LegSvc as AccountPostingLegService
    participant Factory as PostingStrategyFactory
    participant Strategy as Strategy Impl
    participant Ext as External System
    participant Publisher as PostingEventPublisher
    participant Kafka as Kafka

    Note over Processor: Running in retryExecutor thread<br/>MDC: traceId + postingId

    Processor->>PostRepo: findById(postingId=42)
    PostRepo-->>Processor: AccountPosting { status=PENDING, requestPayload=JSON }

    Processor->>Processor: deserialize requestPayload → AccountPostingRequest

    Note over Processor: Step a — Fetch non-SUCCESS legs ordered by legOrder
    Processor->>LegRepo: findNonSuccessByPostingId(postingId=42)
    LegRepo-->>Processor: List<AccountPostingLeg> [leg(id=101,order=1,status=FAILED),<br/>leg(id=102,order=2,status=PENDING)]

    Note over Processor: Step b — Execute each leg's strategy
    loop for each non-SUCCESS leg (ascending legOrder)
        Processor->>Factory: getStrategy(leg.targetSystem)
        Factory-->>Processor: matching Strategy

        Processor->>Strategy: process(accountPostingRequest,<br/>existingLegId=leg.id, isRetry=true)

        Strategy->>Strategy: buildExternalRequest()
        Strategy->>LegSvc: updateLegMode(legId, mode=RETRY,<br/>attemptNumber=leg.attemptNumber+1)

        Strategy->>Ext: submitPosting(externalRequest)

        alt External call succeeds
            Ext-->>Strategy: ExternalCallResult { success=true, referenceId }
            Strategy->>LegSvc: updateLeg(status=SUCCESS, referenceId,<br/>postedTime=NOW(), responsePayload)
            LegSvc-->>Strategy: updated leg
            Strategy-->>Processor: LegResponse { status=SUCCESS }
        else External call fails again
            Ext-->>Strategy: ExternalCallResult { success=false, reason }
            Strategy->>LegSvc: updateLeg(status=FAILED, reason, responsePayload)
            LegSvc-->>Strategy: updated leg
            Strategy-->>Processor: LegResponse { status=FAILED }
        end
    end

    Note over Processor: Step c — Re-evaluate posting status
    Processor->>LegRepo: findAllByPostingId(42)
    LegRepo-->>Processor: all legs

    alt All legs are SUCCESS
        Processor->>PostRepo: save(status=SUCCESS, updatedAt=NOW())
        Note over Processor: Step d — Publish success event
        Processor->>Publisher: publish(PostingSuccessEvent { postingId=42 })
        Publisher->>Kafka: send("posting.success", event)
        Kafka-->>Publisher: ack
        Processor-->>Processor: return RetryResult { postingId=42, outcome=SUCCEEDED }
    else Some legs still FAILED or PENDING
        Processor->>PostRepo: save(status=PENDING, updatedAt=NOW())
        Note over Processor: retry_locked_until expires naturally<br/>posting available for next retry cycle
        Processor-->>Processor: return RetryResult { postingId=42, outcome=STILL_PENDING }
    end
```

---

## Retry Lock State Diagram

```mermaid
flowchart TD
    A[POST /retry received] --> B{postingIds<br/>provided?}
    B -- Yes --> C[findAllById]
    B -- No --> D[findByStatusNot SUCCESS]
    C --> E[lockForRetry UPDATE]
    D --> E
    E --> F{rows actually<br/>updated?}
    F -- 0 rows<br/>all already locked --> G[Return: skipped=N, attempted=0]
    F -- N rows locked --> H[findLockedForRetry]
    H --> I[Dispatch N CompletableFutures<br/>to retryExecutor pool]
    I --> J[allOf.join — wait for all]
    J --> K[Aggregate RetryResponse]
    K --> L[Return to client]

    style G fill:#f9d4d4
    style L fill:#d4f9d4
```

---

## Key Notes

| Aspect | Detail |
|--------|--------|
| **Lock mechanism** | `retry_locked_until` is set atomically in a single `UPDATE ... WHERE retry_locked_until IS NULL OR retry_locked_until < NOW()`. This prevents two concurrent retry requests from picking up the same posting. No DB row lock is held. |
| **Lock expiry** | After 2 minutes, `retry_locked_until` becomes stale and the posting is eligible for another retry cycle automatically. |
| **Parallel execution** | Each posting gets its own `CompletableFuture` on the `retryExecutor` thread pool (configured in `AsyncConfig`). Leg execution within a posting is still sequential (must respect `leg_order`). |
| **isRetry flag** | Strategies receive `isRetry=true`, allowing them to set `mode=RETRY` and increment `attempt_number` on the leg. |
| **Already-locked skip** | Postings where `retry_locked_until > NOW()` are skipped (counted in `skipped` in the response). Prevents stampede on slow retries. |
| **MDC in async threads** | `PostingRetryProcessor` re-seeds MDC with `postingId` and `e2eRef` at the start of each `CompletableFuture` task since MDC is thread-local. |
| **No leg pre-insert on retry** | Unlike create flow, retry uses the existing `PENDING`/`FAILED` legs — it does not create new rows. It only processes legs that are not yet `SUCCESS`. |
