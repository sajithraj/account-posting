# backend-ops-aws — Account Posting Operations Lambda

Java 17 AWS Lambda for the **operations / dashboard API**: search, retry, leg management,
and routing config CRUD. No Spring Boot — Dagger 2 for compile-time DI, AWS SDK v2 for DynamoDB / SQS.

---

## Architecture context

This project is split across three modules:

| Module                                                | Purpose                                                              |
|-------------------------------------------------------|----------------------------------------------------------------------|
| [`backend-aws`](../backend-aws/README.md)             | Posting creation (`POST /`) and SQS consumer                         |
| **`backend-ops-aws`** ← you are here                  | Ops dashboard — search, retry, legs, config CRUD                     |
| [`backend-aws-infra`](../backend-aws-infra/README.md) | Terraform — provisions both Lambdas, DynamoDB, SQS, SNS, API Gateway |

---

## Routes handled

| Method   | Route                                                             | What it does                                        |
|----------|-------------------------------------------------------------------|-----------------------------------------------------|
| `POST`   | `/v3/payment/account-posting/search`                              | Search postings with JSON body filters + pagination |
| `POST`   | `/v3/payment/account-posting/retry`                               | Re-queue specific PNDG/RCVD postings to SQS         |
| `GET`    | `/v3/payment/account-posting/{id}`                                | Fetch posting with all legs                         |
| `GET`    | `/v3/payment/account-posting/{id}/transaction`                    | List all legs for a posting                         |
| `GET`    | `/v3/payment/account-posting/{id}/transaction/{order}`            | Get a single leg by transaction order (1-based int) |
| `PATCH`  | `/v3/payment/account-posting/{id}/transaction/{order}`            | Manual leg status override                          |
| `GET`    | `/v3/payment/account-posting/config`                              | List all routing configs                            |
| `GET`    | `/v3/payment/account-posting/config/{requestType}`                | Configs by request type                             |
| `POST`   | `/v3/payment/account-posting/config`                              | Create a routing config entry                       |
| `PUT`    | `/v3/payment/account-posting/config/{requestType}/{orderSeq}`     | Update a routing config entry                       |
| `DELETE` | `/v3/payment/account-posting/config/{requestType}/{orderSeq}`     | Delete a routing config entry                       |

> **Note:** `{order}` is the leg's integer `transaction_order` (1, 2, 3…), visible in the `legs[]` array in any posting response.
>
> `POST /v3/payment/account-posting` (posting creation) is **not** handled here — it belongs to `backend-aws`.

---

## AWS Services

| Service      | Role                                       | Env var                                                     |
|--------------|--------------------------------------------|-------------------------------------------------------------|
| **DynamoDB** | Read/write postings, legs, routing configs | `POSTING_TABLE_NAME`, `LEG_TABLE_NAME`, `CONFIG_TABLE_NAME` |
| **SQS**      | Publish retry jobs to the processing queue | `PROCESSING_QUEUE_URL`                                      |

No SNS — this Lambda does not publish failure alerts (that is `backend-aws`'s responsibility).

---

## Environment Variables

| Variable               | Description                               | Example                                        |
|------------------------|-------------------------------------------|------------------------------------------------|
| `AWS_ACCOUNT_REGION`   | AWS region                                | `ap-southeast-1`                               |
| `POSTING_TABLE_NAME`   | DynamoDB posting table                    | `account-posting`                              |
| `LEG_TABLE_NAME`       | DynamoDB leg table                        | `account-posting-leg`                          |
| `CONFIG_TABLE_NAME`    | DynamoDB config table                     | `account-posting-config`                       |
| `PROCESSING_QUEUE_URL` | SQS queue URL — retry jobs published here | `https://sqs.ap-southeast-1.amazonaws.com/...` |

---

## Package Structure

```
com.sr.accountposting
├── LambdaRequestHandler                 Entry point — logs request ID, function name, routes to handler
├── handler/
│   └── ApiGatewayHandler                Routes all 12 ops endpoints; logs route matched + request/response
├── service/
│   ├── posting/
│   │   ├── AccountPostingService        Interface: findById, search, retry
│   │   └── AccountPostingServiceImpl    DynamoDB reads + SQS publish for retry; per-posting eligibility logs
│   ├── leg/
│   │   ├── AccountPostingLegService     Interface: listLegs, getLeg, manualUpdateLeg
│   │   └── AccountPostingLegServiceImpl DynamoDB read/update; logs ownership checks and status changes
│   └── config/
│       ├── PostingConfigService         Interface: CRUD for routing configs
│       └── PostingConfigServiceImpl     DynamoDB CRUD; logs each operation with before/after state
├── di/
│   ├── AppComponent                     Dagger component — exposes ApiGatewayHandler only
│   └── AppModule                        Binds services; provides SqsClient
├── infra/
│   └── AwsClientFactory                 Builds DynamoDB / SQS clients (LocalStack-aware)
├── repository/
│   ├── posting/AccountPostingRepository DynamoDB ops for postings; logs each query + result
│   ├── leg/AccountPostingLegRepository  DynamoDB ops for legs; logs each query + result
│   └── config/PostingConfigRepository   DynamoDB ops for configs; logs each query + result
├── entity/     DynamoDB @DynamoDbBean annotated entities
├── dto/        Jackson @JsonProperty snake_case DTOs
├── enums/      PostingStatus, RequestMode, LegStatus, LegMode
├── exception/  BusinessException, ValidationException, TechnicalException, ResourceNotFoundException
└── util/       AppConfig (env vars), JsonUtil, IdGenerator
```

---

## Key Design Notes

- **Search pagination** — `POST /search` (or `GET /search` with query params) accepts `limit` and optional
  `page_token`, and returns `{ "items": [...], "next_page_token": "..." }`. Pass `next_page_token` back as
  `page_token` to fetch the next page. Multiple filters can be combined; the repository queries one matching
  GSI first, then applies the remaining filters, sorts by `updated_at` descending, and paginates. If no
  filters are provided, search defaults to postings updated in the last 3 days.
  Supported filters: `end_to_end_reference_id` (alias `end_to_end_id`), `source_reference_id`
  (alias `source_ref_id`), `source_name`, `status` (alias `posting_status`), `request_type`,
  `from_date`, `to_date`.

- **Retry request** — retry requires explicit `posting_ids`; bulk retry without IDs returns `400`.

- **Retry flow** — each eligible `PNDG`/`RCVD` posting re-publishes its `requestPayload` to SQS with
  `requestMode=RETRY`, then this Lambda updates only the posting `status` to `RTRY`. `backend-aws` owns retry
  processing details and later moves the posting to `ACSP` or back to `PNDG`.

- **Leg identification** — legs are addressed by their integer `transaction_order` (1, 2, 3…) in URL paths.
  The order is visible as `transaction_order` in the `legs[]` array of any posting response. DynamoDB primary
  key for a leg is `(postingId, transactionOrder)`, so these lookups hit the main table directly with no GSI.

- **Manual leg override** — `PATCH .../transaction/{order}` sets `mode=MANUAL`, updates `status`, `reason`,
  and `updatedBy`. `attemptNumber` is not incremented.

- **Config CRUD** — `PUT /config/{requestType}/{orderSeq}` and `DELETE /config/{requestType}/{orderSeq}`
  address entries by their natural composite key (the DynamoDB primary key). No separate lookup needed.
  Routing configs control which external systems (CBS / GL / OBPM) receive legs and in which order.
  Changes take effect on the next create or retry.

- **DI** — Dagger 2 compile-time; no reflection, cold-start safe.

- **Logging** — every Lambda invocation logs the AWS request ID and function name. Each service method logs
  entry, key decision points, and completion. Repository methods log each DynamoDB operation at DEBUG level.
  All error paths include structured context (postingId, transactionId, errorCode).

---

## Build & Test

```bash
# Unit tests only (LocalStackIntegrationTest excluded)
mvn test

# Integration tests — start LocalStack first (see backend-aws-infra)
mvn test -Plocalstack

# Or use the helper script from backend-aws-infra:
#   PowerShell:  .\localstack\run-tests.ps1
#   Bash:        bash localstack/run-tests.sh

# Build fat JAR for Lambda deployment
mvn clean package -DskipTests
# Output: target/account-posting-ops-aws.jar
```

> LocalStack setup (Docker Compose, DynamoDB tables, SQS queue) is managed by
> [`backend-aws-infra`](../backend-aws-infra/README.md). See **Part 1** there.

### Integration test seed strategy

The ops `LocalStackIntegrationTest` seeds its own data in `@BeforeAll`:

- **Configs** — seeded via `POST /config` (the ops endpoint itself)
- **Postings** — written directly to DynamoDB (creation belongs to `backend-aws`)
- **Legs** — written directly to DynamoDB

This means the ops integration tests are self-contained and do not depend on `backend-aws` running.

---

## Lambda Handler Class

```
com.sr.accountposting.LambdaRequestHandler
```

Configured as the `Handler` in `backend-aws-infra/lambda.tf` (ops Lambda entry).
