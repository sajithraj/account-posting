# backend — Account Posting Service

Spring Boot 3.5.12 · Java 17 · Maven · PostgreSQL (prod) / H2 (local) · Kafka (optional)

---

## Prerequisites

| Tool  | Version |
|-------|---------|
| Java  | 17      |
| Maven | 3.9+    |

---

## Run Commands

```bash
# Local dev — H2 in-memory, seed data auto-loaded, no external DB needed
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Dev — connects to PostgreSQL (Docker or local instance on 5432)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Build JAR (skip tests)
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=AccountPostingIntegrationTest
```

---

## Spring Profiles

Each profile has a fully self-contained `application-{env}.yml`.
Credentials are placeholder values — real values are injected by CyberArk at runtime.

| Profile  | Database               | Kafka    | Log Level | Notes                             |
|----------|------------------------|----------|-----------|-----------------------------------|
| `local`  | H2 in-memory           | disabled | DEBUG     | `ddl-auto: create-drop`, seed SQL |
| `dev`    | PostgreSQL :5432       | disabled | DEBUG     |                                   |
| `docker` | PostgreSQL (container) | disabled | INFO      | Used inside Docker Compose stack  |
| `qa`     | PostgreSQL             | disabled | INFO      |                                   |
| `uat`    | PostgreSQL             | disabled | INFO      |                                   |
| `prod`   | PostgreSQL             | disabled | INFO      | Pool size 20                      |

Activate: `-Dspring-boot.run.profiles=local` or env var `SPRING_PROFILES_ACTIVE=dev`

---

## Package Structure

```
com.accountposting
├── AccountPostingApplication.java
│
├── config/
│   ├── JpaConfig.java          @EnableJpaAuditing + @EnableJpaRepositories
│   ├── CacheConfig.java        Caffeine — "configsByRequestType" cache (500 max, 1h TTL)
│   └── AsyncConfig.java        retryExecutor thread pool (core=4, max=10, queue=100)
│
├── entity/
│   ├── BaseEntity.java                    createdAt / updatedAt via JPA auditing (@MappedSuperclass)
│   ├── AccountPostingEntity.java          account_posting table
│   ├── AccountPostingLegEntity.java       account_posting_leg table (@Version for optimistic lock)
│   ├── AccountPostingHistoryEntity.java   account_posting_history table (archive)
│   ├── AccountPostingLegHistoryEntity.java account_posting_leg_history table (archive)
│   ├── PostingConfig.java                 posting_config table
│   └── enums/
│       ├── PostingStatus    PNDG | ACSP | RJCT
│       ├── LegStatus        PENDING | SUCCESS | FAILED
│       ├── LegMode          NORM | RETRY | MANUAL
│       ├── CreditDebitIndicator  CREDIT | DEBIT
│       ├── SourceName       IMX | RMS | STABLECOIN (validation enum)
│       └── RequestType      All valid request type values (validation enum)
│
├── repository/
│   ├── AccountPostingRepository          JpaSpecificationExecutor + custom @Modifying queries:
│   │                                      findEligibleIdsForRetry(), lockEligibleByIds()
│   ├── AccountPostingLegRepository       listByPostingId, listNonSuccessLegs, lockForRetry
│   ├── AccountPostingHistoryRepository   JpaSpecificationExecutor (read-only)
│   ├── AccountPostingLegHistoryRepository findByPostingIdOrderByLegOrder
│   ├── PostingConfigRepository           findByRequestTypeOrderByOrderSeqAsc (@Cacheable)
│   ├── AccountPostingSpecification       dynamic JPA Criteria — active table search
│   └── AccountPostingHistorySpecification dynamic JPA Criteria — history table search
│
├── dto/
│   ├── accountposting/
│   │   ├── AccountPostingRequestV2        Inbound create request
│   │   ├── AccountPostingCreateResponseV2 Response to create (includes leg outcomes)
│   │   ├── AccountPostingFullResponseV2   Full posting + legs (used for search / findById)
│   │   └── AccountPostingSearchRequestV2  Search filter params (@ModelAttribute)
│   ├── accountpostingleg/
│   │   ├── AccountPostingLegRequestV2     Create leg internally
│   │   ├── AccountPostingLegResponseV2    Leg read response
│   │   ├── LegResponseV2                  Leg outcome returned from strategies
│   │   ├── LegCreateResponseV2            Leg outcome inside create response
│   │   ├── UpdateLegRequestV2             Full leg update (internal — not exposed via HTTP)
│   │   └── ManualUpdateRequestV2          PATCH — status + optional reason (UI)
│   ├── config/
│   │   ├── PostingConfigRequestV2         Create/update config
│   │   └── PostingConfigResponseV2        Config read response
│   ├── retry/
│   │   ├── RetryRequestV2                 Optional list of postingIds; empty = retry all
│   │   └── RetryResponseV2                totalPostings / successCount / failedCount
│   └── ExternalCallResultV2               Outcome from a strategy's external call
│
├── mapper/
│   ├── AccountPostingMapperV2     MapStruct — entity ↔ DTO, history ↔ DTO
│   ├── AccountPostingLegMapperV2  MapStruct — leg entity ↔ DTO, factory methods, history ↔ DTO
│   └── PostingConfigMapperV2      MapStruct — config entity ↔ DTO
│
├── service/
│   ├── AccountPostingRequestValidatorV2
│   │     Single validate() — checks SourceName enum, RequestType enum
│   │
│   ├── accountposting/
│   │   ├── AccountPostingServiceV2 (interface)
│   │   ├── AccountPostingServiceImplV2
│   │   │     create / search / findById / retry / searchHistory
│   │   └── strategy/
│   │       ├── PostingStrategy (interface)
│   │       │     getPostingFlow()  — returns "{TARGET_SYSTEM}_{OPERATION}" key
│   │       │     process()         — executes the leg, returns LegResponseV2
│   │       ├── PostingStrategyFactory
│   │       │     Resolves strategy by "{targetSystem}_{operation}" key at startup
│   │       ├── ExternalApiHelper   Builds outbound payloads; stub call methods — REPLACE before go-live
│   │       └── impl/
│   │           ├── CBSPostingService     CBS_POSTING
│   │           ├── GLPostingService      GL_POSTING
│   │           ├── OBPMPostingService    OBPM_POSTING
│   │           ├── CBSAddHoldService     CBS_ADD_HOLD
│   │           └── CBSRemoveHoldService  CBS_REMOVE_HOLD
│   │
│   ├── accountpostingleg/
│   │   ├── AccountPostingLegServiceV2 (interface)
│   │   └── AccountPostingLegServiceImplV2
│   │         addLeg / listLegs / listNonSuccessLegs / getLeg / updateLeg / manualUpdateLeg
│   │
│   ├── retry/
│   │   └── PostingRetryProcessorV2
│   │         Processes one posting's retry in its own @Transactional.
│   │         Called asynchronously from AccountPostingServiceImplV2.
│   │
│   ├── config/
│   │   ├── PostingConfigServiceV2 (interface)
│   │   └── PostingConfigServiceImplV2    CRUD + flushCache()
│   │
│   └── archival/
│       ├── ArchivalServiceV2 (interface)
│       └── ArchivalServiceImplV2
│             @Scheduled job — moves ACSP/RJCT records older than threshold-days to history tables.
│             Configurable: app.archival.enabled, cron, threshold-days, batch-size
│
├── event/
│   ├── PostingSuccessEvent (record)  postingId, e2eRef, requestType, targetSystems, occurredAt
│   └── PostingEventPublisher         Publishes to Kafka. Bean created only when app.kafka.enabled=true.
│
├── exception/
│   ├── GlobalExceptionHandler        Maps exceptions → structured ErrorResponse
│   ├── ResourceNotFoundException     → HTTP 404
│   └── BusinessException             → HTTP 422, carries error code string
│
├── controller/
│   ├── AccountPostingControllerV2    POST / GET / GET/{id} / POST /retry / GET /history
│   ├── AccountPostingLegControllerV2 GET / GET/{legId} / PATCH/{legId}
│   └── PostingConfigControllerV2     GET / POST / PUT/{id} / DELETE/{id} / POST /cache/flush
│
└── utils/
    └── AppUtility    objectMapper.writeValueAsString() wrapper used for logging
```

---

## Key Flows

### POST /v2/payment/account-posting — Create Posting

```
1.  Idempotency check on end_to_end_reference_id → 422 DUPLICATE_E2E_REF if exists
2.  Map request → AccountPostingEntity (status=PNDG), save (audit trail from this point)
3.  requestValidator.validate(request) — checks SourceName + RequestType enums
    └─ failure: set status=RJCT, save response_payload with error, throw BusinessException
4.  Load posting_config for request_type ordered by order_seq (Caffeine-cached per requestType)
    └─ empty: set status=RJCT, throw BusinessException NO_CONFIG_FOUND
5.  Set target_systems = CBS_GL / OBPM / etc. (joined config target names), save
6.  Pre-insert all legs as PENDING before calling any external system
    └─ guarantees every leg is visible and retryable even if an earlier call fails mid-flight
7.  For each pre-inserted leg (in order_seq order):
    a. Resolve PostingStrategy by "{targetSystem}_{operation}" key
    b. strategy.process() → calls ExternalApiHelper stub → returns LegResponseV2
    c. Leg updated to SUCCESS or FAILED
8.  Evaluate outcome:
    └─ all SUCCESS → posting.status = ACSP
    └─ any FAILED  → posting.status = PNDG  (retryable)
9.  Save response_payload (serialised response)
10. If ACSP and Kafka enabled → publish PostingSuccessEvent
```

### POST /v2/payment/account-posting/retry — Retry

```
1.  If posting_ids supplied → use those; else query findEligibleIdsForRetry(PNDG, now)
    └─ eligible = status=PNDG AND (retry_locked_until IS NULL OR retry_locked_until < now)
2.  Atomic lock: single @Modifying JPQL UPDATE sets retry_locked_until = now + 120s
3.  Dispatch one CompletableFuture per locked posting to retryExecutor thread pool
    └─ MDC context (traceId etc.) propagated to each async thread
4.  PostingRetryProcessorV2.process(postingId) — own @Transactional per posting:
    a. Deserialise original request from request_payload
    b. Fetch non-SUCCESS legs (PENDING or FAILED)
    c. For each leg: resolve strategy by "{targetSystem}_{operation}", call, update leg
    d. Re-evaluate all legs → ACSP if all SUCCESS, else PNDG
    e. Always clear retry_locked_until (posting immediately retryable again)
    f. If ACSP and Kafka enabled → publish PostingSuccessEvent
5.  CompletableFuture.allOf().join() — wait for all
6.  Return RetryResponseV2 { totalPostings, successCount, failedCount }
```

### GET /v2/payment/account-posting — Search

```
AccountPostingSpecification.from(criteria)
  Builds JPA Criteria predicates dynamically — every field is optional and composable.
  Filters: status, sourceName, requestType, sourceReferenceId, endToEndReferenceId,
           targetSystem (LIKE), fromDate, toDate
  Returns Spring Data Page with leg list per posting fetched separately.
```

### GET /v2/payment/account-posting/{postingId} — Find by ID

```
1. Check account_posting table
2. If not found → transparent fallback to account_posting_history
3. Throws 404 only if not found in either table
```

### Archival Job

```
Runs on schedule (default: daily 02:00, configurable via app.archival.cron)
Moves records older than app.archival.threshold-days (default 90) in batches:
  account_posting      → account_posting_history
  account_posting_leg  → account_posting_leg_history
Processes app.archival.batch-size rows per committed transaction.
Disabled on local profile (app.archival.enabled=false).
```

---

## API Reference

Base URL: `http://localhost:8080`
All JSON uses **snake_case** (Jackson `SNAKE_CASE` naming strategy applied globally).

### Account Posting

| Method | Path                                      | Status | Description                                        |
|--------|-------------------------------------------|--------|----------------------------------------------------|
| `POST` | `/v2/payment/account-posting`             | 201    | Submit a new posting                               |
| `GET`  | `/v2/payment/account-posting`             | 200    | Search active postings (paginated)                 |
| `GET`  | `/v2/payment/account-posting/{postingId}` | 200    | Get posting + all legs (active + history fallback) |
| `POST` | `/v2/payment/account-posting/retry`       | 200    | Retry PNDG postings                                |
| `GET`  | `/v2/payment/account-posting/history`     | 200    | Search archived postings (paginated)               |

**Create request body:**

```json
{
  "source_reference_id": "SRC-1001",
  "end_to_end_reference_id": "E2E-1001",
  "source_name": "IMX",
  "request_type": "IMX_CBS_GL",
  "amount": 1500.00,
  "currency": "USD",
  "credit_debit_indicator": "CREDIT",
  "debtor_account": "1324658900",
  "creditor_account": "9875312300",
  "requested_execution_date": "2026-03-23",
  "remittance_information": "Invoice payment"
}
```

**Search query parameters:**

| Param                     | Type               | Match      |
|---------------------------|--------------------|------------|
| `status`                  | `PNDG\|ACSP\|RJCT` | exact      |
| `source_name`             | string             | exact      |
| `request_type`            | string             | exact      |
| `source_reference_id`     | string             | exact      |
| `end_to_end_reference_id` | string             | exact      |
| `target_system`           | string             | LIKE       |
| `from_date`               | `yyyy-MM-dd`       | >=         |
| `to_date`                 | `yyyy-MM-dd`       | <=         |
| `page`                    | int                | 0-based    |
| `size`                    | int                | default 20 |

### Posting Legs

| Method  | Path                                                  | Status | Description                          |
|---------|-------------------------------------------------------|--------|--------------------------------------|
| `GET`   | `/v2/payment/account-posting/{postingId}/leg`         | 200    | List all legs ordered by leg_order   |
| `GET`   | `/v2/payment/account-posting/{postingId}/leg/{legId}` | 200    | Get a single leg                     |
| `PATCH` | `/v2/payment/account-posting/{postingId}/leg/{legId}` | 200    | Manual status override (mode=MANUAL) |

> **Note:** `PUT` leg endpoint is intentionally absent. `legService.updateLeg()` is called in-process
> by strategy services only — it is not exposed over HTTP.

**PATCH body:**

```json
{
  "status": "SUCCESS",
  "reason": "Confirmed by ops team"
}
```

### Posting Config

| Method   | Path                                               | Status | Description                      |
|----------|----------------------------------------------------|--------|----------------------------------|
| `GET`    | `/v2/payment/account-posting/config`               | 200    | List all config entries          |
| `GET`    | `/v2/payment/account-posting/config/{requestType}` | 200    | Get routing for one request type |
| `POST`   | `/v2/payment/account-posting/config`               | 201    | Create a config entry            |
| `PUT`    | `/v2/payment/account-posting/config/{configId}`    | 200    | Update a config entry            |
| `DELETE` | `/v2/payment/account-posting/config/{configId}`    | 204    | Delete a config entry            |
| `POST`   | `/v2/payment/account-posting/config/cache/flush`   | 204    | Flush the in-memory config cache |

> Config entries are Caffeine-cached per `requestType` (500 max entries, 1h TTL).
> Always call `POST /config/cache/flush` after any config change.

### Error Response Structure

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "ERROR_CODE",
  "message": "Human-readable description",
  "errors": [
    {
      "field": "amount",
      "message": "must be greater than 0"
    }
  ]
}
```

| HTTP | Code                     | Cause                                            |
|------|--------------------------|--------------------------------------------------|
| 400  | `VALIDATION_FAILED`      | Bean validation failure — includes `errors[]`    |
| 400  | `INVALID_REQUEST_BODY`   | Malformed / unparseable JSON body                |
| 400  | `INVALID_ENUM_VALUE`     | Unknown `source_name` or `request_type`          |
| 404  | `NOT_FOUND`              | Posting or config entry does not exist           |
| 422  | `DUPLICATE_E2E_REF`      | Duplicate `end_to_end_reference_id`              |
| 422  | `NO_CONFIG_FOUND`        | No `posting_config` for the given `request_type` |
| 422  | `DUPLICATE_CONFIG_ORDER` | Unique constraint on `(request_type, order_seq)` |
| 500  | `INTERNAL_ERROR`         | Unexpected server error                          |

---

## Health & Monitoring

| Endpoint                | Description        |
|-------------------------|--------------------|
| `GET /actuator/health`  | Application health |
| `GET /actuator/info`    | Build info         |
| `GET /actuator/metrics` | Micrometer metrics |

---

## Design Notes

### Leg Decoupling

`AccountPostingLegEntity.postingId` is a plain `Long` column — not a JPA `@ManyToOne`.
The `leg` package never imports from `posting`.
**Why:** Prevents accidental cascade operations, avoids N+1 lazy-loading pitfalls, and keeps the
packages independently deployable. Fetching is always explicit.

### Pre-insert Legs

All legs are saved as `PENDING` before any external call is made. If a call fails mid-flight,
every leg already exists in the DB and is immediately retryable without any special recovery path.

### Retry Lock Design

Two-phase lock — no `SELECT FOR UPDATE`, no `SKIP LOCKED`:

1. `findEligibleIdsForRetry()` — JPQL SELECT (no lock) identifies candidates
2. `lockEligibleByIds()` — single `@Modifying` JPQL UPDATE sets `retry_locked_until = now + 120s`

The lock is cleared by `PostingRetryProcessorV2` at the end of processing (success or failure),
so the posting is immediately retryable again. Works on H2, PostgreSQL, and Oracle.

### Strategy Pattern

Each target system × operation combination is a separate `@Component`:

| Strategy key      | Class                | Target system call    |
|-------------------|----------------------|-----------------------|
| `CBS_POSTING`     | CBSPostingService    | CBS core banking post |
| `GL_POSTING`      | GLPostingService     | General ledger post   |
| `OBPM_POSTING`    | OBPMPostingService   | OBPM post             |
| `CBS_ADD_HOLD`    | CBSAddHoldService    | CBS add hold          |
| `CBS_REMOVE_HOLD` | CBSRemoveHoldService | CBS remove hold       |

`PostingStrategyFactory` builds a `Map<String, PostingStrategy>` at startup keyed by
`getPostingFlow()`. Add a new target system by implementing `PostingStrategy` — no other
changes needed.

> **All strategy `call*()` methods in `ExternalApiHelper` are stubs.
> Replace them with real HTTP client calls before go-live.**

### JSON Payload Columns

`request_payload` and `response_payload` are stored as JSON.
Annotated with `@JdbcTypeCode(SqlTypes.JSON)` — Hibernate maps to:

- `JSONB` on PostgreSQL
- `JSON` on H2
- `CLOB` / native JSON on Oracle

### Kafka

`PostingEventPublisher` is conditionally created (`@ConditionalOnProperty("app.kafka.enabled", havingValue="true")`).
The service null-checks the publisher before use — Kafka being disabled never causes a NullPointerException.

### Archival

`ArchivalServiceImplV2` moves records older than `threshold-days` from active to history tables
in configurable batches. Each batch runs in its own transaction (`TransactionTemplate`).
`GET /v2/payment/account-posting/{postingId}` is archive-transparent — it falls back to the
history table automatically if the posting is not found in the active table.

---

## Testing

```bash
mvn test                                         # all tests
mvn test -Dtest=AccountPostingIntegrationTest    # integration tests only
mvn test -Dtest=AccountPostingServiceImplTest    # unit tests only
```

Integration tests use H2 in-memory with `application-integration-test.yml`.
Strategy stubs are used in tests — no real external system calls.
