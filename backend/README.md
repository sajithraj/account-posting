# account-posting — Spring Boot API

Spring Boot 3.5 · Java 17 · Maven · PostgreSQL · Kafka (optional)

---

## Run Commands

```bash
# Local development
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Build (skip tests)
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=AccountPostingServiceImplTest
```

---

## Spring Profiles

Each environment has a fully self-contained `application-{env}.yml`. None of them inherit from `application.yml` — all
config keys are explicitly set. Credentials are hardcoded as placeholders; CyberArk injects the real values at runtime.

| Profile  | File                     | Notes                                          |
|----------|--------------------------|------------------------------------------------|
| `local`  | `application-local.yml`  | Kafka disabled, show-sql=true, DEBUG logging   |
| `dev`    | `application-dev.yml`    | Kafka enabled, DEBUG logging                   |
| `qa`     | `application-qa.yml`     | Kafka enabled, INFO logging                    |
| `uat`    | `application-uat.yml`    | Kafka enabled, INFO logging                    |
| `prod`   | `application-prod.yml`   | Kafka enabled, pool-size 20, WARN root logging |
| `docker` | `application-docker.yml` | Kafka enabled, INFO logging                    |

Activate with: `-Dspring-boot.run.profiles=local` (or set `SPRING_PROFILES_ACTIVE` env var in K8s).

---

## Package Structure

```
com.accountposting
├── AccountPostingApplication.java
├── config/
│   ├── JpaConfig.java              @EnableJpaAuditing + @EnableJpaRepositories
│   ├── CacheConfig.java            Caffeine cache — "configsByRequestType" (500 max, 1h TTL)
│   └── AsyncConfig.java            retryExecutor thread pool (core=4, max=10, queue=100)
├── common/
│   ├── entity/BaseEntity.java      createdAt / updatedAt via JPA auditing
│   ├── response/ApiResponse.java   Generic { success, data, error, timestamp } envelope
│   ├── response/ApiError.java      Error detail + field-level validation errors
│   └── exception/
│       ├── GlobalExceptionHandler  404 / 422 / 400 / 500 mapped to ApiResponse
│       ├── ResourceNotFoundException → HTTP 404
│       └── BusinessException         → HTTP 422, carries error code string
├── entity/
│   ├── AccountPostingEntity        account_posting table
│   ├── AccountPostingLegEntity     account_posting_leg table
│   ├── PostingConfig               posting_config table
│   └── enums/                      PostingStatus (PNDG/ACSP/RJCT), LegStatus (PENDING/SUCCESS/FAILED), LegMode, CreditDebitIndicator
├── repository/
│   ├── AccountPostingRepository    JpaSpecificationExecutor + custom @Modifying queries
│   ├── AccountPostingLegRepository listByPostingId, lockForRetry
│   ├── PostingConfigRepository     findByRequestTypeOrderByOrderSeqAsc (@Cacheable)
│   └── AccountPostingSpecification dynamic JPA Criteria predicates for search
├── dto/                            Request/Response DTOs — all snake_case over the wire
├── mapper/
│   ├── AccountPostingMapper        MapStruct — entity ↔ DTO
│   └── AccountPostingLegMapper     MapStruct — leg entity ↔ DTO, factory methods
├── service/
│   ├── accountposting/
│   │   ├── AccountPostingServiceImpl          create / search / findById / retry
│   │   ├── AccountPostingRequestValidator     single validate() — field + enum checks
│   │   └── strategy/
│   │       ├── PostingStrategy (interface)    getPostingFlow() + process()
│   │       ├── PostingStrategyFactory         resolves by "{targetSystem}_{operation}" key
│   │       ├── CBSPostingService              CBS_POSTING — stub, replace with real HTTP client
│   │       ├── GLPostingService               GL_POSTING  — stub, replace with real HTTP client
│   │       ├── OBPMPostingService             OBPM_POSTING — stub, replace with real HTTP client
│   │       ├── CBSAddHoldService              CBS_ADD_HOLD — stub (ADD_ACCOUNT_HOLD flow)
│   │       ├── CBSRemoveHoldService           CBS_REMOVE_HOLD — stub (BUY_CUSTOMER_POSTING flow)
│   │       └── ExternalApiHelper              builds outbound payloads + stub call methods
│   ├── accountpostingleg/
│   │   └── AccountPostingLegServiceImpl       addLeg / listLegs / getLeg / updateLeg / manualUpdate
│   ├── retry/
│   │   └── PostingRetryProcessor              parallel retry — one CompletableFuture per posting
│   └── config/
│       └── PostingConfigServiceImpl           CRUD + cache flush
├── event/
│   ├── PostingSuccessEvent (record)            postingId, e2eRef, requestType, targetSystems
│   └── PostingEventPublisher                  @ConditionalOnProperty(kafka.enabled=true)
├── controller/
│   ├── AccountPostingController               POST / GET / GET/{id} / POST /retry
│   ├── AccountPostingLegController            POST / GET / GET/{legId} / PUT / PATCH
│   └── PostingConfigController                CRUD + GET /{requestType} + POST /cache/flush
└── util/
    └── MappingUtils                           objectMapper.writeValueAsString() wrapper
```

---

## Key Flows

### POST /account-posting

1. Idempotency guard on `endToEndReferenceId`
2. Map request → entity (status=PENDING), save to DB
3. `validate(request)` — field checks then enum membership; throws `BusinessException` on failure (posting saved as
   FAILED for audit trail)
4. Load `posting_config` ordered by `order_seq` ascending (cached by `requestType`)
5. For each config entry: insert PENDING leg → call `ExternalApiHelper` stub → update leg to SUCCESS/FAILED
6. Update posting status (SUCCESS if all legs SUCCESS, else FAILED) + save response payload
7. Publish `PostingSuccessEvent` to Kafka if all SUCCESS and Kafka enabled

### POST /account-posting/retry

1. Resolve candidate IDs — if none supplied, `findEligibleIdsForRetry(PNDG, now)` returns IDs where `status=PNDG` and the lock has expired or is null
2. Lock atomically: `lockEligibleByIds` (`@Modifying` JPQL UPDATE) sets `retry_locked_until = NOW() + 2 min`
3. Dispatch one `CompletableFuture` per locked ID to the `retryExecutor` thread pool
4. Each `PostingRetryProcessorV2.process()` runs in its own `@Transactional`: retries non-SUCCESS legs via the matching strategy (key = `{targetSystem}_{operation}`), updates posting to `ACSP` or `PNDG`, and **clears `retry_locked_until`** so the posting is immediately retryable again

### GET /account-posting (search)

`AccountPostingSpecification.from(criteria)` composes JPA Criteria predicates — every field is optional.

---

## API Reference

All request/response bodies use **snake_case** (configured globally via Jackson `SNAKE_CASE` naming strategy).

### Account Posting

| Method | Path                           | Status | Description                     |
|--------|--------------------------------|--------|---------------------------------|
| `POST` | `/account-posting`             | 201    | Submit a new posting            |
| `GET`  | `/account-posting`             | 200    | Search with filters (paginated) |
| `GET`  | `/account-posting/{postingId}` | 200    | Get posting with all legs       |
| `POST` | `/account-posting/retry`       | 200    | Retry non-SUCCESS legs on PNDG postings |

**POST /account-posting — request body:**

```json
{
  "source_reference_id": "SRC-1001",
  "end_to_end_reference_id": "E2E-1001",
  "source_name": "IMX",
  "request_type": "IMX_CBS_GL",
  "amount": 1500.00,
  "currency": "USD",
  "credit_debit_indicator": "CREDIT",
  "debtor_account": "13246589",
  "creditor_account": "98753123",
  "requested_execution_date": "2026-03-22",
  "remittance_information": "salary payment"
}
```

**GET /account-posting — query parameters:**

| Param                     | Type                       | Description                |
|---------------------------|----------------------------|----------------------------|
| `status`                  | `PNDG\|ACSP\|RJCT`        | Filter by posting status   |
| `source_reference_id`     | string                     | Exact match                |
| `end_to_end_reference_id` | string                     | Exact match                |
| `source_name`             | string                     | Exact match                |
| `request_type`            | string                     | Exact match                |
| `target_system`           | string                     | Partial match (LIKE)       |
| `from_date`               | `yyyy-MM-dd`               | Execution date range start |
| `to_date`                 | `yyyy-MM-dd`               | Execution date range end   |
| `page`                    | int                        | 0-based (default 0)        |
| `size`                    | int                        | Page size (default 20)     |

### Legs

| Method  | Path                                       | Status | Description                          |
|---------|--------------------------------------------|--------|--------------------------------------|
| `POST`  | `/account-posting/{postingId}/leg`         | 201    | Add a leg manually                   |
| `GET`   | `/account-posting/{postingId}/leg`         | 200    | List all legs                        |
| `GET`   | `/account-posting/{postingId}/leg/{legId}` | 200    | Get a single leg                     |
| `PUT`   | `/account-posting/{postingId}/leg/{legId}` | 200    | Full leg update                      |
| `PATCH` | `/account-posting/{postingId}/leg/{legId}` | 200    | Manual status override (mode=MANUAL) |

### Posting Config

| Method   | Path                                    | Status | Description                             |
|----------|-----------------------------------------|--------|-----------------------------------------|
| `GET`    | `/account-posting/config`               | 200    | List all config entries                 |
| `GET`    | `/account-posting/config/{requestType}` | 200    | Get targets for a request type (cached) |
| `POST`   | `/account-posting/config`               | 201    | Create a config entry                   |
| `PUT`    | `/account-posting/config/{configId}`    | 200    | Update a config entry                   |
| `DELETE` | `/account-posting/config/{configId}`    | 204    | Delete a config entry                   |
| `POST`   | `/account-posting/config/cache/flush`   | 204    | Flush the config cache                  |

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

| HTTP | Code                     | Cause                                          |
|------|--------------------------|------------------------------------------------|
| 400  | `VALIDATION_FAILED`      | @NotBlank / @NotNull / @Size constraint        |
| 400  | `INVALID_REQUEST_BODY`   | Malformed JSON                                 |
| 400  | `INVALID_ENUM_VALUE`     | Unknown sourceName or requestType              |
| 404  | `NOT_FOUND`              | Resource does not exist                        |
| 422  | `DUPLICATE_E2E_REF`      | Duplicate endToEndReferenceId                  |
| 422  | `NO_CONFIG_FOUND`        | No posting_config for the given requestType    |
| 422  | `DUPLICATE_CONFIG_ORDER` | Unique constraint on (request_type, order_seq) |
| 500  | `INTERNAL_ERROR`         | Unexpected server error                        |

---

## Design Notes

- **Leg decoupling** — `AccountPostingLeg.postingId` is a plain `Long` column, not a `@ManyToOne`. The `leg` package
  never imports from `posting`.
- **Pre-insert legs** — All legs are saved as `PENDING` before any external call. If a call fails mid-flight, every leg
  already exists for retry.
- **Two-step retry lock** — `findEligibleIdsForRetry` (JPQL SELECT) + `lockEligibleByIds` (`@Modifying` JPQL UPDATE) sets `retry_locked_until = NOW() + 2 min`. The processor clears the lock after processing so the posting is immediately retryable. H2 and PostgreSQL compatible (no `UPDATE ... RETURNING`).
- **ExternalApiHelper** — All build/stub methods for CBS, GL, and OBPM live in one `@Component`. Replace stub `call*()`
  methods with real HTTP clients before go-live.
- **Kafka conditional** — `PostingEventPublisher` is only wired when `app.kafka.enabled=true`. The service null-checks
  the publisher before calling it.
- **Caffeine cache** — `posting_config` entries are cached per `requestType` with a 1-hour TTL. Use
  `POST /config/cache/flush` after any config change.
- **Snake_case** — Jackson `SNAKE_CASE` naming strategy is configured globally. All JSON bodies in and out are
  snake_case. URL query parameters remain camelCase (Jackson does not apply naming strategies to `@ModelAttribute`).
- **No HTTP between packages** — `posting` and `leg` packages run in the same JVM and communicate via direct method
  calls. `AccountPostingServiceImpl` injects `AccountPostingLegService` directly.

---

## Health & Metrics

| Endpoint                    | Description        |
|-----------------------------|--------------------|
| `GET /api/actuator/health`  | Application health |
| `GET /api/actuator/info`    | Build info         |
| `GET /api/actuator/metrics` | Micrometer metrics |
