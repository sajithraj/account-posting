# State Machine Diagrams

Status transition diagrams for both `AccountPosting` and `AccountPostingLeg`. Each diagram shows all valid states, transition triggers, and guard conditions.

---

## Posting Status State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING : POST /account-posting<br/>AccountPosting created

    PENDING --> SUCCESS : All legs reach SUCCESS<br/>[computed after Loop 2 completes<br/>or after PostingRetryProcessor finishes]

    PENDING --> PENDING : At least one leg remains FAILED or PENDING<br/>[retry cycle completes but not all legs succeeded]

    SUCCESS --> [*] : Terminal state<br/>PostingSuccessEvent published to Kafka

    note right of PENDING
        retry_locked_until IS NOT NULL
        means a retry is currently in flight.
        New retry requests skip this posting.
        Lock expires automatically after 2 minutes.
    end note

    note right of SUCCESS
        Terminal — no further transitions.
        Idempotency guard prevents re-submission
        of same endToEndReferenceId.
    end note
```

---

## Posting Status — Extended Transition Table

```mermaid
flowchart TD
    START([" "]) -->|POST /account-posting| PENDING

    PENDING -->|"All legs = SUCCESS\n(create flow, Loop 2 complete)"| SUCCESS
    PENDING -->|"Any leg FAILED or PENDING\n(create flow)"| PENDING_WAIT

    PENDING_WAIT([PENDING\nwaiting for retry]) -->|"POST /retry\nlock acquired"| LOCKED

    LOCKED -->|"PostingRetryProcessor runs\nAll legs → SUCCESS"| SUCCESS
    LOCKED -->|"PostingRetryProcessor runs\nSome legs still FAILED"| PENDING_WAIT

    LOCKED -->|"retry_locked_until expires\n(2 min timeout)"| PENDING_WAIT

    SUCCESS --> TERMINAL(["SUCCESS\nTerminal"])

    style START fill:#ffffff,stroke:#ffffff
    style TERMINAL fill:#d4f9d4,stroke:#4caf50
    style SUCCESS fill:#d4f9d4,stroke:#4caf50
    style PENDING fill:#fff3cd,stroke:#ff9800
    style PENDING_WAIT fill:#fff3cd,stroke:#ff9800
    style LOCKED fill:#cce5ff,stroke:#007bff
```

---

## Leg Status State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING : Leg pre-inserted before Loop 2<br/>mode=NORM, attempt_number=1

    PENDING --> SUCCESS : Strategy receives SUCCESS from external system<br/>[posted_time set, referenceId stored]

    PENDING --> FAILED : Strategy receives FAILED from external system<br/>[reason stored, no posted_time]

    FAILED --> SUCCESS : Retry cycle — strategy re-calls external system<br/>external returns SUCCESS<br/>[mode=RETRY, attempt_number incremented,<br/>posted_time set]

    FAILED --> FAILED : Retry cycle — external system still fails<br/>[mode=RETRY, attempt_number incremented,<br/>reason updated]

    SUCCESS --> [*] : Terminal — not re-processed by retry<br/>(findNonSuccessByPostingId excludes SUCCESS legs)

    note right of PENDING
        Created by preSaveLeg() before any external call.
        Ensures leg exists even if first strategy throws.
    end note

    note right of FAILED
        attempt_number incremented on each retry.
        Stays FAILED until external system succeeds
        or posting is abandoned (manual intervention).
    end note
```

---

## Leg Status Transitions — Detailed

```mermaid
flowchart TD
    PRE([Pre-insert\npreSaveLeg]) -->|"Loop 1 — before any external call"| PENDING_LEG

    PENDING_LEG[PENDING] -->|"Loop 2 or Retry:\nStrategy calls external system\nResult = SUCCESS"| SUCCESS_LEG

    PENDING_LEG -->|"Loop 2:\nStrategy calls external system\nResult = FAILED or Exception"| FAILED_LEG

    FAILED_LEG -->|"Retry cycle:\nfindNonSuccessByPostingId\nmode=RETRY, attempt++ \nExternal returns SUCCESS"| SUCCESS_LEG

    FAILED_LEG -->|"Retry cycle:\nExternal still fails\nattempt++ reason updated"| FAILED_LEG

    SUCCESS_LEG[SUCCESS\nTerminal] --> SKIP([Excluded from\nfuture retries])

    style PRE fill:#e8e8e8,stroke:#999
    style PENDING_LEG fill:#fff3cd,stroke:#ff9800
    style FAILED_LEG fill:#f9d4d4,stroke:#f44336
    style SUCCESS_LEG fill:#d4f9d4,stroke:#4caf50
    style SKIP fill:#e8e8e8,stroke:#999
```

---

## Combined Status Relationship

```mermaid
flowchart LR
    subgraph Posting["AccountPosting.status"]
        AP_PENDING[PENDING]
        AP_SUCCESS[SUCCESS]
    end

    subgraph Legs["AccountPostingLeg.status (per leg)"]
        L_PENDING[PENDING]
        L_SUCCESS[SUCCESS]
        L_FAILED[FAILED]
    end

    AP_PENDING <-->|"Derived from\nleg statuses"| Legs

    L_PENDING & L_FAILED -->|"Any leg not SUCCESS\n→ posting stays PENDING"| AP_PENDING
    L_SUCCESS -->|"ALL legs SUCCESS\n→ posting becomes SUCCESS"| AP_SUCCESS

    style AP_SUCCESS fill:#d4f9d4,stroke:#4caf50
    style AP_PENDING fill:#fff3cd,stroke:#ff9800
    style L_SUCCESS fill:#d4f9d4,stroke:#4caf50
    style L_PENDING fill:#fff3cd,stroke:#ff9800
    style L_FAILED fill:#f9d4d4,stroke:#f44336
```

---

## Key Notes

| Rule | Detail |
|------|--------|
| **Posting SUCCESS requires all legs SUCCESS** | The `AccountPostingService` checks every leg after Loop 2. If any leg is not `SUCCESS`, the posting remains `PENDING`. |
| **SUCCESS is terminal for legs** | `findNonSuccessByPostingId()` filters out `SUCCESS` legs. Once a leg succeeds it is never re-processed. |
| **No FAILED terminal for postings** | A posting never permanently transitions to `FAILED` in the normal flow — it stays `PENDING` so it can be retried. `FAILED` is reserved for use cases like manual intervention or hard-fail business rules. |
| **Retry lock is not a status** | The retry lock (`retry_locked_until`) is a timestamp column on the posting, not a distinct status value. A locked posting still shows `PENDING`. |
| **mode column tracks how a leg was last processed** | `NORM` = original create flow; `RETRY` = processed by retry; `MANUAL` = manually triggered outside normal flows. |
