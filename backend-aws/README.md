# backend-aws — Account Posting Creation Lambda

Java 17 AWS Lambda for **posting creation and async job processing**.
No Spring Boot — Dagger 2 for compile-time DI, AWS SDK v2 for DynamoDB / SQS / SNS.

---

## Architecture context

This project is split across three modules:

| Module | Purpose |
|--------|---------|
| **`backend-aws`** ← you are here | Posting creation (`POST /`) and SQS consumer |
| [`backend-ops-aws`](../backend-ops-aws/README.md) | Ops dashboard — search, retry, legs, config CRUD |
| [`backend-aws-infra`](../backend-aws-infra/README.md) | Terraform — provisions both Lambdas, DynamoDB, SQS, SNS, API Gateway |

---

## Routes handled

| Trigger | Route | What it does |
|---------|-------|-------------|
| API Gateway | `POST /v2/payment/account-posting` | Validate → persist → sync-process or queue to SQS |
| SQS | `PROCESSING_QUEUE_URL` | Consume `PostingJob` messages, run legs, alert SNS on failure |

All other routes (`/search`, `/retry`, `/{id}`, `/config`, `/transaction`) return **404** here — they belong to `backend-ops-aws`.

---

## AWS Services

| Service | Role | Env var |
|---------|------|---------|
| **DynamoDB** | Read/write postings, legs, routing configs | `POSTING_TABLE_NAME`, `LEG_TABLE_NAME`, `CONFIG_TABLE_NAME` |
| **SQS** | Publish async jobs; Lambda triggered by same queue | `PROCESSING_QUEUE_URL` |
| **SNS** | Failure alerts published by `SqsHandler` | `SUPPORT_ALERT_TOPIC_ARN` |

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `AWS_ACCOUNT_REGION` | AWS region | `ap-southeast-1` |
| `POSTING_TABLE_NAME` | DynamoDB posting table | `account-posting` |
| `LEG_TABLE_NAME` | DynamoDB leg table | `account-posting-leg` |
| `CONFIG_TABLE_NAME` | DynamoDB config table | `account-posting-config` |
| `PROCESSING_QUEUE_URL` | SQS queue URL for async job publishing | `https://sqs.ap-southeast-1.amazonaws.com/...` |
| `SUPPORT_ALERT_TOPIC_ARN` | SNS topic ARN for failure alerts | `arn:aws:sns:ap-southeast-1:...:posting-alerts` |

---

## Package Structure

```
com.sr.accountposting
├── LambdaRequestHandler              Entry point — routes SQS vs API Gateway events
├── handler/
│   ├── ApiGatewayHandler             POST /v2/payment/account-posting only (all others → 404)
│   └── SqsHandler                    SQS consumer — processes PostingJob, alerts SNS on failure
├── service/
│   ├── posting/
│   │   ├── AccountPostingService     Interface: create(IncomingPostingRequest)
│   │   └── AccountPostingServiceImpl Validate → persist → async(SQS) or sync(processor)
│   ├── processor/
│   │   ├── PostingProcessorService   Interface: process(PostingJob, configs)
│   │   └── PostingProcessorServiceImpl  Per-leg strategy execution
│   └── leg/
│       ├── AccountPostingLegService  Interface: addLeg, updateLeg
│       └── AccountPostingLegServiceImpl
├── service/strategy/
│   ├── PostingStrategy               Per-system interface (CBS / GL / OBPM)
│   └── PostingStrategyFactory        Resolves strategy by targetSystem
├── di/
│   ├── AppComponent                  Dagger component — exposes ApiGatewayHandler + SqsHandler
│   └── AppModule                     Binds services; provides SqsClient + SnsClient
├── infra/
│   └── AwsClientFactory              Builds DynamoDB / SQS / SNS clients (LocalStack-aware)
├── repository/
│   ├── posting/AccountPostingRepository
│   ├── leg/AccountPostingLegRepository
│   └── config/PostingConfigRepository
├── entity/     DynamoDB @DynamoDbBean annotated entities
├── dto/        Jackson @JsonProperty snake_case DTOs
├── enums/      PostingStatus, RequestMode
├── exception/  BusinessException, ValidationException, TechnicalException, ResourceNotFoundException
└── util/       AppConfig (env vars), IdGenerator (Snowflake IDs + TTL), JsonUtil
```

---

## Key Design Notes

- **Sync vs async** — if all routing configs for the `requestType` have `processingMode=ASYNC`, the posting is queued to SQS and returns `ACSP` immediately. Otherwise `PostingProcessorService` is called inline.
- **Idempotency** — `endToEndReferenceId` uniqueness checked before persisting.
- **SQS batch isolation** — each SQS record wrapped in try/catch; one bad message does not block the rest of the batch.
- **SNS swallowed** — `SqsHandler.alertSupportTeam` catches publish failures so they don't cause an unwanted SQS retry.
- **DI** — Dagger 2 compile-time; no reflection, no cold-start penalty from classpath scanning.
- **IDs** — Snowflake-style via `IdGenerator.nextId()`. TTL epoch set to `AppConfig.TTL_DAYS` days out.

---

## SQS Message Format

Published to `PROCESSING_QUEUE_URL` on async create and on retry (from `backend-ops-aws`):

```json
{
  "posting_id": 1234567890123,
  "request_payload": { "...full IncomingPostingRequest..." },
  "request_mode": "NORM"
}
```

`request_mode` is `NORM` for new postings, `RETRY` for requeued postings.

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
# Output: target/account-posting-aws.jar  (referenced by Terraform in backend-aws-infra)
```

> LocalStack setup (Docker Compose, DynamoDB tables, SQS queue, SNS topic) is managed by
> [`backend-aws-infra`](../backend-aws-infra/README.md). See **Part 1** there.

---

## Lambda Handler Class

```
com.sr.accountposting.LambdaRequestHandler
```

Configured as the `Handler` in `backend-aws-infra/lambda.tf`.
