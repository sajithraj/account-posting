# Database Design

Full entity-relationship diagram for the Account Posting Orchestrator database. The schema uses PostgreSQL and is
managed via Flyway migrations in the `db/` module.

---

## Entity-Relationship Diagram

```mermaid
erDiagram

    account_posting {
        bigserial posting_id PK
        varchar100 source_reference_id "NOT NULL"
        varchar100 end_to_end_reference_id "NOT NULL UNIQUE"
        varchar100 source_name "NOT NULL"
        varchar50  request_type "NOT NULL"
        numeric19_4 amount "NOT NULL"
        char3 currency "NOT NULL"
        varchar6 credit_debit_indicator "NOT NULL CHECK IN CREDIT DEBIT"
        varchar50 debtor_account "NOT NULL"
        varchar50 creditor_account "NOT NULL"
        date requested_execution_date "NOT NULL"
        varchar500 remittance_information "NULLABLE"
        varchar10 status "NOT NULL DEFAULT PENDING CHECK IN PENDING SUCCESS FAILED"
        varchar target_systems "NULLABLE — ordered CSV of target system names"
        jsonb request_payload "Serialized AccountPostingRequest"
        jsonb response_payload "Serialized response after processing"
        timestamptz retry_locked_until "NULL = not locked; set to NOW+2min on retry dispatch"
        timestamptz created_at "NOT NULL DEFAULT NOW()"
        timestamptz updated_at "NOT NULL DEFAULT NOW()"
    }

    account_posting_leg {
        bigserial posting_leg_id PK
        bigint posting_id FK "NOT NULL → account_posting(posting_id)"
        int leg_order "NOT NULL — execution sequence within the posting"
        varchar100 leg_type "NOT NULL — e.g. DEBIT, CREDIT, GL_ENTRY"
        varchar100 target_system "NOT NULL — CBS / GL / OBPM"
        varchar50 account "NOT NULL"
        varchar10 status "NOT NULL DEFAULT PENDING CHECK IN PENDING SUCCESS FAILED"
        varchar100 reference_id "NULLABLE — reference returned by external system"
        varchar500 reason "NULLABLE — failure reason or external message"
        int attempt_number "NOT NULL DEFAULT 1 — incremented on each retry"
        timestamptz posted_time "NULLABLE — set when leg reaches SUCCESS"
        jsonb request_payload "Payload sent to external system"
        jsonb response_payload "Raw response received from external system"
        varchar10 mode "NOT NULL DEFAULT NORM CHECK IN NORM RETRY MANUAL"
        varchar20 operation "NOT NULL DEFAULT POSTING"
        timestamptz created_at "NOT NULL DEFAULT NOW()"
        timestamptz updated_at "NOT NULL DEFAULT NOW()"
    }

    posting_config {
        bigserial config_id PK
        varchar source_name "NOT NULL — e.g. PAYMENT_HUB"
        varchar request_type "NOT NULL — e.g. INTRA_BANK"
        varchar target_system "NOT NULL — CBS / GL / OBPM"
        varchar operation "NOT NULL — POSTING / REVERSAL"
        int order_seq "NOT NULL — execution order for this target system"
        timestamptz created_at "NULLABLE"
        timestamptz updated_at "NULLABLE"
    }

    account_posting ||--o{ account_posting_leg : "has legs (postingId FK)"
    account_posting }o--|| posting_config : "resolved by requestType + sourceName"
```

---

## Schema Notes

### account_posting

| Column                    | Purpose                                                                                                                                                                       |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `end_to_end_reference_id` | **Idempotency key** — UNIQUE constraint prevents duplicate submissions                                                                                                        |
| `status`                  | Aggregate status. `PENDING` = at least one leg not yet successful. `SUCCESS` = all legs succeeded. `FAILED` = reserved for hard failures (not currently used in normal flow). |
| `target_systems`          | Comma-separated ordered list of target systems derived from `posting_config` at create time — stored for auditability                                                         |
| `request_payload`         | Full original request serialized as JSONB — used by retry to reconstruct the `AccountPostingRequest`                                                                          |
| `response_payload`        | Aggregated response from all strategy executions                                                                                                                              |
| `retry_locked_until`      | Optimistic retry lock. A posting whose `retry_locked_until > NOW()` is currently being retried by another thread and is skipped by new retry requests                         |

### account_posting_leg

| Column           | Purpose                                                                                                       |
|------------------|---------------------------------------------------------------------------------------------------------------|
| `leg_order`      | Determines the execution sequence within a posting. Strategies execute sequentially in ascending `leg_order`. |
| `target_system`  | Identifies which strategy (`CBSPostingService`, `GLPostingService`, `OBPMPostingService`) handles this leg    |
| `mode`           | `NORM` = original submission; `RETRY` = retry execution; `MANUAL` = manually triggered                        |
| `attempt_number` | Incremented on each retry cycle — useful for auditing how many attempts were made                             |
| `posted_time`    | Timestamp set when the leg transitions to `SUCCESS`                                                           |

### posting_config

| Column                                 | Purpose                                                               |
|----------------------------------------|-----------------------------------------------------------------------|
| `source_name` + `request_type`         | Together identify the configuration entry for a given posting request |
| `target_system`                        | One row per target system per request type                            |
| `order_seq`                            | Determines execution order of strategies. Lower = executed first.     |
| UNIQUE `(request_type, target_system)` | Prevents duplicate configuration rows                                 |

### Relationships

- `account_posting` **1 → many** `account_posting_leg` via `posting_id` FK. The relationship is intentionally **not**
  modelled as a JPA `@ManyToOne` in the `leg` package — `postingId` is a plain `Long` column to maintain package
  decoupling.
- `posting_config` is **not** a FK relationship — it is looked up at runtime by
  `PostingConfigRepository.findBySourceNameAndRequestType()` and does not appear in the entity graph.
