# Account Posting — AWS Lambda (`backend-aws`)

Java 17 Lambda project. Handles both API Gateway (REST) and SQS (async processing) events from a single unified handler.
No Spring Boot — uses Dagger 2 for DI, AWS SDK v2 for DynamoDB / SQS / SNS / SSM.

---

## Prerequisites

| Tool    | Version                             |
|---------|-------------------------------------|
| Java    | 17+                                 |
| Maven   | 3.8+                                |
| Docker  | For LocalStack local dev            |
| AWS CLI | For deploying / testing against AWS |

---

## Project Structure

```
src/main/java/com/accountposting/
├── handler/
│   ├── UnifiedHandler.java        ← Single Lambda entry point (API GW + SQS)
│   ├── ApiGatewayHandler.java     ← Routes REST requests by path + method
│   └── SqsHandler.java            ← Unified processor (NORM & RETRY flows)
├── di/
│   ├── AppModule.java             ← Dagger providers (AWS clients, repos, services)
│   └── AppComponent.java          ← Dagger component interface
├── posting/
│   ├── entity/AccountPostingEntity.java
│   ├── dto/                       ← Request / response DTOs
│   ├── repository/
│   ├── service/
│   └── validator/
├── leg/
│   ├── entity/AccountPostingLegEntity.java
│   ├── dto/
│   ├── repository/
│   └── service/
├── config/
│   ├── entity/PostingConfigEntity.java
│   ├── repository/
│   └── service/
├── strategy/
│   ├── PostingStrategy.java       ← Interface
│   ├── PostingStrategyFactory.java
│   ├── ExternalApiHelper.java     ← Builds + calls CBS / GL / OBPM (stub → real HTTP)
│   └── impl/
│       ├── CBSPostingStrategy.java
│       ├── GLPostingStrategy.java
│       ├── OBPMPostingStrategy.java
│       ├── CBSAddHoldStrategy.java
│       └── CBSRemoveHoldStrategy.java
├── client/dto/ExternalCallResult.java
├── common/
├── enums/
├── infra/AwsClientFactory.java    ← Singleton AWS SDK clients
└── util/
```

---

## Environment Variables

Set these before running locally or configure them in Terraform (`lambda.tf`).

| Variable                  | Description                     | Example                       |
|---------------------------|---------------------------------|-------------------------------|
| `AWS_ACCOUNT_REGION`      | AWS region                      | `ap-southeast-1`              |
| `POSTING_TABLE_NAME`      | DynamoDB posting table          | `account-posting-dev-posting` |
| `LEG_TABLE_NAME`          | DynamoDB leg table              | `account-posting-dev-leg`     |
| `CONFIG_TABLE_NAME`       | DynamoDB config table           | `account-posting-dev-config`  |
| `PROCESSING_QUEUE_URL`    | SQS queue URL                   | `https://sqs.../...`          |
| `SUPPORT_ALERT_TOPIC_ARN` | SNS topic ARN                   | `arn:aws:sns:...`             |
| `DYNAMO_TTL_DAYS`         | DynamoDB item TTL in days       | `60`                          |
| `LOCALSTACK_ENDPOINT`     | Override endpoint for local dev | `http://localhost:4566`       |

---

## Build

```bash
# Build fat JAR (required before terraform apply)
mvn clean package

# Output JAR path (referenced by Terraform)
target/account-posting-aws.jar
```

---

## Run Locally with LocalStack

```bash
# 1. Start LocalStack (from project root)
docker compose up -d

# 2. Wait ~10 seconds, then verify resources
aws --endpoint-url=http://localhost:4566 dynamodb list-tables --region ap-southeast-1

# 3. Set local environment variables
export LOCALSTACK_ENDPOINT=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_ACCOUNT_REGION=ap-southeast-1
export POSTING_TABLE_NAME=account-posting-local-posting
export LEG_TABLE_NAME=account-posting-local-leg
export CONFIG_TABLE_NAME=account-posting-local-config
export PROCESSING_QUEUE_URL=http://localhost:4566/000000000000/account-posting-local-processing-queue
export SUPPORT_ALERT_TOPIC_ARN=arn:aws:sns:ap-southeast-1:000000000000:account-posting-local-support-alert
export DYNAMO_TTL_DAYS=60
```

You can invoke the Lambda handler locally by calling `UnifiedHandler.handleRequest()` directly in a test or via the AWS
SAM CLI.

---

## API Endpoints

Base path: `/v2/payment/account-posting`

| Method   | Path                               | Description                             |
|----------|------------------------------------|-----------------------------------------|
| `POST`   | `/`                                | Create new posting                      |
| `POST`   | `/search`                          | Search postings (equality + date range) |
| `GET`    | `/{postingId}`                     | Get posting by ID with legs             |
| `POST`   | `/retry`                           | Retry PNDG / RECEIVED postings          |
| `GET`    | `/{postingId}/transaction`         | List all legs                           |
| `GET`    | `/{postingId}/transaction/{order}` | Get single leg                          |
| `PATCH`  | `/{postingId}/transaction/{order}` | Manual leg override                     |
| `GET`    | `/config`                          | Get all PostingConfig entries           |
| `GET`    | `/config/{requestType}`            | Get configs by requestType              |
| `POST`   | `/config`                          | Create PostingConfig                    |
| `PUT`    | `/config/{requestType}/{orderSeq}` | Update PostingConfig                    |
| `DELETE` | `/config/{requestType}/{orderSeq}` | Delete PostingConfig                    |

---

## SQS Message Format

Published by `/posting` (NORM) and `/retry` (RETRY) to the processing queue:

```json
{
  "posting_id": 1234567890123,
  "request_payload": { ...full IncomingPostingRequest... },
  "request_mode": "NORM"
}
```

---

## Implementing Real External Calls

`ExternalApiHelper` contains stub implementations for CBS, GL, and OBPM. Replace the `callCbs()`, `callGl()`, and
`callObpm()` methods with real HTTP client calls:

```java
// Example — replace stub in callCbs()
HttpRequest req = HttpRequest.newBuilder()
    .uri(URI.create(CBS_URL))
    .header("Authorization", "Bearer " + token)
    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(cbsRequest)))
    .build();
HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
```

---

## Dagger DI

All dependencies are wired at cold start via `DaggerAppComponent`. To add a new dependency:

1. Add `@Inject` to the constructor of your class, or
2. Add a `@Provides` method in `AppModule` if it needs AWS clients or env vars
