# Sequence Diagram — Retry Flow

Detailed sequence for `POST /account-posting/retry`. Highlights the atomic lock mechanism, parallel `CompletableFuture`
dispatch, and per-posting retry processing.

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

    Note over Svc: Step 1 — Resolve candidate IDs
    alt postingIds provided in request
        Svc->>Svc: use provided IDs directly
    else no postingIds — retry all eligible PNDG
        Svc->>PostRepo: findEligibleIdsForRetry(status=PNDG, now)
        Note right of PostRepo: SELECT postingId WHERE status=PNDG<br/>AND (retryLockedUntil IS NULL<br/>  OR retryLockedUntil < now)
        PostRepo-->>Svc: List<Long> [42, 43]
    end

    Note over Svc: Step 2 — Atomic lock via single @Modifying UPDATE
    Svc->>PostRepo: lockEligibleByIds(ids, status=PNDG, now, lockUntil=NOW()+2min)
    Note right of PostRepo: JPQL UPDATE account_posting<br/>SET retry_locked_until = :lockUntil<br/>WHERE postingId IN :ids<br/>AND status = :status<br/>AND (retryLockedUntil IS NULL<br/>  OR retryLockedUntil < :now)
    PostRepo-->>Svc: rowsLocked (int)

    Note over Svc: Step 3 — Dispatch one CompletableFuture per locked ID
    loop for each locked ID
        Svc->>Executor: CompletableFuture.supplyAsync(<br/>  () -> processor.process(id), retryExecutor)
    end

    Note over Svc: Step 4 — Wait for all futures
    Svc->>Svc: CompletableFuture.allOf(futures).join()

    Svc->>Svc: Aggregate LegRetryResults into RetryResponse
    Svc-->>Ctrl: RetryResponse { totalLegsRetried: 2, successCount: 1, failedCount: 1 }
    Ctrl-->>Client: 200 OK { data: RetryResponse }
```

---

## Retry Flow — Per-Posting Processing (PostingRetryProcessor)

```mermaid
sequenceDiagram
    autonumber

    participant Processor as PostingRetryProcessorV2
    participant PostRepo as AccountPostingRepository
    participant LegSvc as AccountPostingLegServiceV2
    participant Factory as PostingStrategyFactory
    participant Strategy as Strategy Impl<br/>(CBS/GL/OBPM/CBS_ADD_HOLD/CBS_REMOVE_HOLD)
    participant Ext as External System
    participant Publisher as PostingEventPublisher
    participant Kafka as Kafka

    Note over Processor: Running in retryExecutor thread<br/>@Transactional — owns its own TX<br/>MDC: traceId + postingId + e2eRef

    Processor->>PostRepo: findById(postingId=42)
    PostRepo-->>Processor: AccountPostingEntity { status=PNDG, requestPayload=JSON }

    Processor->>Processor: deserializeRequest(requestPayload)<br/>— handles H2 double-encoded JSONB via JsonNode.isTextual()

    Note over Processor: Step a — Fetch non-SUCCESS legs ordered by legOrder
    Processor->>LegSvc: listNonSuccessLegs(postingId=42)
    LegSvc-->>Processor: List [leg(id=101,order=1,status=FAILED),<br/>leg(id=102,order=2,status=PENDING)]

    Note over Processor: Step b — Execute each leg's strategy
    loop for each non-SUCCESS leg (ascending legOrder)
        Processor->>Factory: resolve(targetSystem + "_" + operation)
        Note right of Factory: key examples:<br/>CBS_POSTING | GL_POSTING | OBPM_POSTING<br/>CBS_ADD_HOLD | CBS_REMOVE_HOLD
        Factory-->>Processor: matching PostingStrategy

        Processor->>Strategy: process(postingId, legOrder, request,<br/>isRetry=true, existingLegId=leg.id)

        Strategy->>Strategy: buildExternalRequest()
        Strategy->>Ext: callExternal(request)

        alt External call succeeds
            Ext-->>Strategy: response { status=SUCCESS, referenceId }
            Strategy->>LegSvc: updateLeg(legId, status=SUCCESS, referenceId,<br/>postedTime=NOW(), mode=RETRY, responsePayload)
            LegSvc-->>Strategy: updated leg
            Strategy-->>Processor: LegResponseV2 { status=SUCCESS }
        else External call fails again
            Ext-->>Strategy: response { status=FAILED, reason }
            Strategy->>LegSvc: updateLeg(legId, status=FAILED, reason, mode=RETRY)
            LegSvc-->>Strategy: updated leg
            Strategy-->>Processor: LegResponseV2 { status=FAILED }
        end
    end

    Note over Processor: Step c — Re-evaluate posting status
    Processor->>LegSvc: listLegs(postingId=42)
    LegSvc-->>Processor: all legs

    Note over Processor: Step d — Clear the retry lock (always)
    Processor->>PostRepo: save(retryLockedUntil=null)
    Note right of PostRepo: Lock always cleared after processing<br/>so posting is immediately retryable again

    alt All legs are SUCCESS
        Processor->>PostRepo: save(status=ACSP, reason="Request processed successfully")
        Note over Processor: Step e — Publish success event
        Processor->>Publisher: publish(PostingSuccessEvent { postingId=42 })
        Publisher->>Kafka: send("posting.success", event)
        Kafka-->>Publisher: ack
        Processor-->>Processor: return List<LegRetryResult> (all SUCCESS)
    else Some legs still FAILED or PENDING
        Processor->>PostRepo: save(status=PNDG, reason=lastFailReason)
        Processor-->>Processor: return List<LegRetryResult> (mixed)
    end
```

---

## Retry Lock State Diagram

```mermaid
flowchart TD
    A[POST /retry received] --> B{postingIds<br/>provided?}
    B -- Yes --> C[use provided IDs directly]
    B -- No --> D[findEligibleIdsForRetry<br/>status=PNDG AND lock expired/null]
    C --> E[lockEligibleByIds @Modifying UPDATE]
    D --> E
    E --> F{rowsLocked > 0?}
    F -- 0 rows<br/>all already locked or none eligible --> G[Return: totalLegsRetried=0]
    F -- N rows locked --> I[Dispatch N CompletableFutures<br/>to retryExecutor pool]
    I --> J[allOf.join — wait for all]
    J --> K[Aggregate RetryResponse]
    K --> L[Return to client]

    style G fill:#f9d4d4
    style L fill:#d4f9d4
```

---

## Key Notes

| Aspect                         | Detail                                                                                                                                                                                                                                                  |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Lock mechanism**             | Two JPQL steps: `findEligibleIdsForRetry` (SELECT) → `lockEligibleByIds` (@Modifying UPDATE). The UPDATE's WHERE clause re-checks `status=PNDG AND lock expired/null`, so races between concurrent callers are harmless. No DB row lock is held.        |
| **Lock cleared after retry**   | `PostingRetryProcessorV2` always sets `retry_locked_until = null` after processing, regardless of outcome. The posting is therefore immediately eligible for the next retry cycle instead of waiting 2 minutes.                                          |
| **Lock TTL fallback**          | The 2-minute TTL (`LOCK_TTL_SECONDS = 120`) protects against processor crashes. If the JVM dies mid-retry, the lock expires naturally and the posting becomes eligible again.                                                                            |
| **Strategy resolution**        | `PostingStrategyFactory.resolve(targetSystem + "_" + operation)` — e.g. `CBS_POSTING`, `GL_POSTING`, `OBPM_POSTING`, `CBS_ADD_HOLD`, `CBS_REMOVE_HOLD`. Adding a new operation requires only a new `@Service` implementing `PostingStrategy`.           |
| **Parallel execution**         | Each posting gets its own `CompletableFuture` on the `retryExecutor` thread pool (configured in `AsyncConfig`). Leg execution within a posting is still sequential (must respect `leg_order`).                                                          |
| **isRetry flag**               | Strategies receive `isRetry=true`, causing them to set `mode=RETRY` on the leg update.                                                                                                                                                                  |
| **MDC in async threads**       | Parent MDC map is captured before dispatch and restored inside each `CompletableFuture` task, since MDC is thread-local.                                                                                                                                 |
| **No leg pre-insert on retry** | Unlike the create flow, retry uses the existing `PENDING`/`FAILED` legs — it does not create new rows. It only processes legs not yet `SUCCESS`.                                                                                                        |
| **H2 JSONB compatibility**     | `deserializeRequest()` uses `JsonNode.isTextual()` to detect H2's double-encoding of JSONB columns (`columnDefinition = "jsonb"`). If the root token is a string, the inner text value is deserialized. PostgreSQL is unaffected.                       |
