# Sequence Diagram — Create Posting Flow

Detailed end-to-end sequence for `POST /account-posting`. Shows every participant from the inbound HTTP request through strategy execution, leg updates, and optional Kafka publishing.

---

## Full Create Sequence

```mermaid
sequenceDiagram
    autonumber

    participant Client as Upstream Caller
    participant MDC as MdcLoggingFilter
    participant Ctrl as AccountPostingController
    participant Svc as AccountPostingService
    participant CfgRepo as PostingConfigRepository
    participant PostRepo as AccountPostingRepository
    participant LegSvc as AccountPostingLegService
    participant Factory as PostingStrategyFactory
    participant Strategy as CBSPostingService<br/>(example strategy)
    participant Ext as External System Stub<br/>(CBS / GL / OBPM)
    participant Publisher as PostingEventPublisher
    participant Kafka as Kafka Topic<br/>(posting.success)

    Client->>MDC: POST /account-posting<br/>{ endToEndReferenceId, requestType, amount, ... }
    MDC->>MDC: Extract X-Correlation-Id header<br/>Seed MDC: traceId, requestType
    MDC->>Ctrl: Forward request

    Ctrl->>Svc: createPosting(AccountPostingRequest)

    Note over Svc: Step 1 — Idempotency guard
    Svc->>PostRepo: existsByEndToEndReferenceId(e2eRef)
    alt Duplicate detected
        PostRepo-->>Svc: true
        Svc-->>Ctrl: throw BusinessException(DUPLICATE_REFERENCE, 422)
        Ctrl-->>Client: 422 { error: "DUPLICATE_REFERENCE" }
    else New request
        PostRepo-->>Svc: false
    end

    Note over Svc: Step 2 — Load posting config
    Svc->>CfgRepo: findBySourceNameAndRequestType(sourceName, requestType)
    CfgRepo-->>Svc: List<PostingConfig> ordered by order_seq<br/>[CBS_POSTING(1), GL_POSTING(2)]

    Note over Svc: Step 3 — Persist AccountPosting (PENDING)
    Svc->>PostRepo: save(AccountPosting { status=PENDING, requestPayload=JSON })
    PostRepo-->>Svc: AccountPosting { postingId=42 }
    Svc->>MDC: MDC.put("postingId", 42)

    Note over Svc: Step 4 — Pre-insert ALL legs as PENDING (Loop 1)
    loop for each PostingConfig entry
        Svc->>LegSvc: preSaveLeg(postingId=42, config, legOrder, accountPostingRequest)
        LegSvc->>LegSvc: Build AccountPostingLeg { status=PENDING, mode=NORM }
        LegSvc-->>Svc: AccountPostingLeg { legId=101, legOrder=1, status=PENDING }
    end

    Note over Svc: Step 5 — Execute strategies sequentially (Loop 2)
    loop for each PostingConfig entry (same order)
        Svc->>Factory: getStrategy("CBS_POSTING")
        Factory-->>Svc: CBSPostingService

        Svc->>Strategy: process(AccountPostingRequest, existingLegId=101, isRetry=false)

        Strategy->>Strategy: buildExternalRequest(request, leg)
        Strategy->>LegSvc: updateLegRequest(legId=101, requestPayload)
        LegSvc-->>Strategy: updated

        Strategy->>Ext: submitPosting(externalRequest)

        alt External call succeeds
            Ext-->>Strategy: ExternalCallResult { success=true, referenceId="CBS-REF-001" }
            Strategy->>LegSvc: updateLeg(UpdateLegRequest { legId=101, status=SUCCESS,<br/>referenceId, responsePayload, postedTime })
            LegSvc-->>Strategy: AccountPostingLeg { status=SUCCESS }
            Strategy-->>Svc: LegResponse { status=SUCCESS }
        else External call fails
            Ext-->>Strategy: ExternalCallResult { success=false, reason="Timeout" }
            Strategy->>LegSvc: updateLeg(UpdateLegRequest { legId=101, status=FAILED, reason })
            LegSvc-->>Strategy: AccountPostingLeg { status=FAILED }
            Strategy-->>Svc: LegResponse { status=FAILED }
        end
    end

    Note over Svc: Step 6 — Compute final status
    Svc->>Svc: allLegsSuccess? → status=SUCCESS<br/>any FAILED/PENDING? → status=PENDING

    Note over Svc: Step 7 — Persist final status + responsePayload
    Svc->>PostRepo: save(AccountPosting { status=SUCCESS/PENDING, responsePayload=JSON })
    PostRepo-->>Svc: saved

    Note over Svc: Step 8 — Publish event (if SUCCESS + Kafka enabled)
    alt status == SUCCESS and kafka.enabled=true
        Svc->>Publisher: publish(PostingSuccessEvent { postingId=42, e2eRef, requestType })
        Publisher->>Kafka: send("posting.success", PostingSuccessEvent)
        Kafka-->>Publisher: ack
    else Kafka disabled or not SUCCESS
        Svc->>Svc: skip publish (null guard / bean absent)
    end

    Note over Svc: Step 9 — Build and return response
    Svc->>LegSvc: getLegsByPostingId(42)
    LegSvc-->>Svc: List<AccountPostingLeg>
    Svc->>Svc: AccountPostingMapper.toResponse(posting, legs)
    Svc-->>Ctrl: AccountPostingResponse

    Ctrl-->>Client: 200 OK { success: true, data: AccountPostingResponse }
```

---

## Key Notes

| Step | Detail |
|------|--------|
| **Idempotency** | `end_to_end_reference_id` has a UNIQUE DB constraint as a second line of defence, in addition to the explicit `existsBy` check |
| **Pre-insert legs (Loop 1)** | All legs are inserted as `PENDING` before Loop 2 begins. If the first external call throws an uncaught exception, the remaining `PENDING` legs still exist in the DB and are available for retry |
| **Strategy lookup** | `PostingStrategyFactory` holds a `Map<String, PostingStrategy>` keyed by `getPostingFlow()`. No conditional logic — pure map lookup |
| **existingLegId** | Strategies receive the pre-inserted `legId` so they update the correct row rather than creating a new one |
| **Sequential execution** | Strategies execute one at a time in `order_seq` order. If a leg fails, execution continues for remaining legs (all legs are attempted) |
| **Final status logic** | `SUCCESS` only if every leg is `SUCCESS`. Any `FAILED` or still-`PENDING` leg keeps the posting in `PENDING` state so it can be retried |
| **MDC enrichment** | `postingId` and `e2eRef` are added to MDC after the posting is saved, enriching all subsequent log lines in the same thread |
