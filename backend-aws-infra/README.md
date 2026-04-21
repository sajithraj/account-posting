# backend-aws-infra — AWS Infrastructure (Terraform)

Terraform configuration for the full AWS deployment. Provisions **two Lambda functions**
(posting creation + ops dashboard), three DynamoDB tables, SQS queue, SNS alert topic,
HTTP API Gateway v2, IAM roles, and CloudWatch alarms.

---

## Architecture overview

```
                        ┌─────────────────────────────────────┐
                        │          API Gateway v2 (HTTP)       │
                        │   /v2/payment/account-posting/...    │
                        └────────────┬────────────────┬────────┘
                                     │                │
                      POST /  only   │                │  All other routes
                                     ▼                ▼
                         ┌──────────────┐    ┌──────────────────┐
                         │  backend-aws │    │ backend-ops-aws  │
                         │  (creation + │    │  (search, retry, │
                         │  SQS worker) │    │   legs, config)  │
                         └──────┬───────┘    └────────┬─────────┘
                                │                     │
              ┌─────────────────┼─────────────────────┤
              │                 │                     │
              ▼                 ▼                     ▼
         DynamoDB            SQS queue            DynamoDB
   (posting/leg/config)   (async jobs)       (posting/leg/config)
              │                 │
              │   (on failure)  ▼
              │              SNS topic
              │           (support alert)
              └─────────────────────────────────────────────────
```

| Module | Lambda | Handles |
|--------|--------|---------|
| [`backend-aws`](../backend-aws/README.md) | `{project}-{env}-handler` | `POST /` create + SQS consumer |
| [`backend-ops-aws`](../backend-ops-aws/README.md) | `{project}-{env}-ops-handler` | Search, retry, legs, config CRUD |

---

## AWS Resources Provisioned

| Resource | Name pattern | Detail |
|----------|-------------|--------|
| Lambda | `{project}-{env}-handler` | `backend-aws` — creation + SQS, Java 17, SnapStart |
| Lambda | `{project}-{env}-ops-handler` | `backend-ops-aws` — ops dashboard, Java 17, SnapStart |
| API Gateway | `{project}-{env}-api` | HTTP API v2, routes split across both Lambdas |
| SQS Queue | `{project}-{env}-processing-queue` | Async posting jobs, `ReportBatchItemFailures` enabled |
| DynamoDB | `{project}-{env}-posting` | PK=postingId, 3 GSIs, TTL 60 days, PITR enabled |
| DynamoDB | `{project}-{env}-leg` | PK=postingId + SK=transactionOrder, TTL, PITR |
| DynamoDB | `{project}-{env}-config` | PK=requestType + SK=orderSeq — routing rules |
| SNS Topic | `{project}-{env}-support-alert` | Email alert on async leg failures |
| IAM Role | `{project}-{env}-lambda-execution-role` | DynamoDB + SQS + SNS least-privilege |
| IAM Role | `{project}-{env}-ops-lambda-role` | DynamoDB + SQS least-privilege (no SNS) |
| CloudWatch | `/aws/lambda/{project}-{env}-handler` | Lambda log group + error alarm |
| CloudWatch | `/aws/lambda/{project}-{env}-ops-handler` | Ops Lambda log group |
| CloudWatch | `/aws/apigateway/{project}-{env}-api` | API Gateway access log group |

---

## API Routes by Lambda

| Method | Path | Lambda |
|--------|------|--------|
| `POST` | `/v2/payment/account-posting` | **backend-aws** |
| `POST` | `/v2/payment/account-posting/search` | **backend-ops-aws** |
| `POST` | `/v2/payment/account-posting/retry` | **backend-ops-aws** |
| `GET` | `/v2/payment/account-posting/{id}` | **backend-ops-aws** |
| `GET` | `/v2/payment/account-posting/{id}/transaction` | **backend-ops-aws** |
| `GET` | `/v2/payment/account-posting/{id}/transaction/{order}` | **backend-ops-aws** |
| `PATCH` | `/v2/payment/account-posting/{id}/transaction/{order}` | **backend-ops-aws** |
| `GET` | `/v2/payment/account-posting/config` | **backend-ops-aws** |
| `GET` | `/v2/payment/account-posting/config/{requestType}` | **backend-ops-aws** |
| `POST` | `/v2/payment/account-posting/config` | **backend-ops-aws** |
| `PUT` | `/v2/payment/account-posting/config/{type}/{order}` | **backend-ops-aws** |
| `DELETE` | `/v2/payment/account-posting/config/{type}/{order}` | **backend-ops-aws** |

---

## Prerequisites

| Tool | Min version | Check |
|------|------------|-------|
| Docker Desktop | — | `docker --version` |
| AWS CLI | 2.x | `aws --version` |
| Terraform | 1.5.0 | `terraform -version` |
| Java | 17 | `java -version` |
| Maven | 3.8+ | `mvn -version` |

---

## Part 1 — Local Development with LocalStack

Full end-to-end test on your machine before touching AWS.

### Step 1 — Start LocalStack

`docker-compose.yml` and `localstack/init.sh` are included here. The init script runs automatically
on startup and creates all DynamoDB tables (with GSIs), the SQS queue, and the SNS topic.

> **Windows only — fix line endings first.**
> ```bash
> git config core.autocrlf false
> git add --renormalize localstack/init.sh
> ```
> Or open `localstack/init.sh` in VS Code, click **CRLF** in the bottom-right, select **LF**, save.

```bash
cd backend-aws-infra
docker compose up -d
```

### Step 2 — Confirm LocalStack is ready

```bash
docker compose ps        # STATUS should show "healthy"
docker compose logs localstack
```

Expected init output:

```
==> Creating DynamoDB tables
Table 'account-posting' created
Table 'account-posting-leg' created
Table 'account-posting-config' created
==> Creating SQS queue
Queue 'posting-queue' created
==> Creating SNS topic
SNS topic 'posting-alerts' ready
==> LocalStack init complete
```

Optionally verify:

```bash
aws --endpoint-url=http://localhost:4566 --region ap-southeast-1 dynamodb list-tables
aws --endpoint-url=http://localhost:4566 --region ap-southeast-1 sqs list-queues
```

### Step 3 — Build both Lambda JARs

```bash
cd ../backend-aws     && mvn clean package -DskipTests
cd ../backend-ops-aws && mvn clean package -DskipTests
cd ../backend-aws-infra
```

Confirm:

```bash
ls ../backend-aws/target/account-posting-aws.jar
ls ../backend-ops-aws/target/account-posting-ops-aws.jar
```

### Step 4 — Set environment variables

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCOUNT_REGION=ap-southeast-1
export POSTING_TABLE_NAME=account-posting
export LEG_TABLE_NAME=account-posting-leg
export CONFIG_TABLE_NAME=account-posting-config
export PROCESSING_QUEUE_URL=http://localhost:4566/000000000000/posting-queue
export SUPPORT_ALERT_TOPIC_ARN=arn:aws:sns:ap-southeast-1:000000000000:posting-alerts
```

`AWS_ENDPOINT_URL` is picked up automatically by AWS SDK v2 (2.21+) — no code change needed.

### Step 5 — Run integration tests

Tests directly instantiate each Lambda handler and drive it with simulated API Gateway / SQS events —
no Lambda deployment or server needed.

**PowerShell (Windows):**

```powershell
.\localstack\run-tests.ps1
```

**Git Bash / WSL:**

```bash
bash localstack/run-tests.sh
```

The scripts run both `backend-aws` and `backend-ops-aws` integration tests in sequence.

#### backend-aws integration tests cover

| Order | Test | Validates |
|-------|------|-----------|
| 1 | `createPosting_async_returnsAcspStatus` | `POST /` with IMX_CBS_GL queues SQS job, returns ACSP |
| 2 | `createPosting_duplicateE2eRef_returns422` | Duplicate endToEndReferenceId → 422 |
| 3 | `processAsyncPosting_viaSqsEvent` | Reads SQS message, processes CBS+GL legs, marks ACSP |
| 4 | `createPosting_sync_returnsImmediateResult` | `POST /` with ADD_ACCOUNT_HOLD processes inline |
| 5 | `createPosting_unknownRequestType_returns400` | Unknown requestType → 400 |
| 6 | `createPosting_missingAmount_returns400` | Missing amount field → 400 |
| 7–9 | `*Route_notHandledByThisLambda*` | search / retry / config routes → 404 |

#### backend-ops-aws integration tests cover

| Order | Test | Validates |
|-------|------|-----------|
| 1–8 | Config CRUD | Create, get all, get by type, update, delete config rows |
| 9–10 | Find by ID | Fetch seeded posting with legs; missing ID → 404 |
| 11–13 | Search | By status, by sourceName, no filters |
| 14–15 | Retry | Re-queue all PNDG, re-queue specific ID |
| 16–20 | Legs | List legs, get single leg, manual update, not-found → 404 |
| 21 | Create route boundary | POST / → 404 (belongs to backend-aws) |

### Step 6 — Seed config (CLI alternative)

If you prefer CLI over the test auto-seed:

```bash
LS="http://localhost:4566"

# IMX_CBS_GL — async (CBS + GL legs)
aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"IMX_CBS_GL"},"orderSeq":{"N":"1"},"sourceName":{"S":"IMX"},"targetSystem":{"S":"CBS"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"IMX_CBS_GL"},"orderSeq":{"N":"2"},"sourceName":{"S":"IMX"},"targetSystem":{"S":"GL"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

# ADD_ACCOUNT_HOLD — sync (CBS hold leg)
aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"ADD_ACCOUNT_HOLD"},"orderSeq":{"N":"1"},"sourceName":{"S":"STABLECOIN"},"targetSystem":{"S":"CBS"},"operation":{"S":"ADD_HOLD"},"processingMode":{"S":"SYNC"}}'
```

### Docker Compose lifecycle

| Command | What it does |
|---------|-------------|
| `docker compose up -d` | Start in background |
| `docker compose ps` | Show status and health |
| `docker compose logs -f localstack` | Stream init + runtime logs |
| `docker compose stop` | Pause — data preserved in named volume |
| `docker compose start` | Resume a stopped container |
| `docker compose restart` | Restart and re-run init (skips existing resources) |
| `docker compose down` | Stop and remove container — **volume kept** |
| `docker compose down -v` | Stop, remove container **and wipe all data** |

---

## Part 2 — AWS Deploy

### Step 1 — Clear LocalStack environment variables

```bash
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_ENDPOINT_URL
unset AWS_ACCOUNT_REGION POSTING_TABLE_NAME LEG_TABLE_NAME
unset CONFIG_TABLE_NAME PROCESSING_QUEUE_URL SUPPORT_ALERT_TOPIC_ARN
```

### Step 2 — Configure AWS credentials

**Option A — AWS CLI (recommended for local dev)**

```bash
aws configure
# Enter key, secret, region (ap-southeast-1), output (json)
aws sts get-caller-identity   # verify
```

**Option B — Environment variables (CI)**

```bash
export AWS_ACCESS_KEY_ID=<key>
export AWS_SECRET_ACCESS_KEY=<secret>
export AWS_DEFAULT_REGION=ap-southeast-1
```

**Option C — SSO / named profile**

```bash
aws sso login --profile my-profile
export AWS_PROFILE=my-profile
```

### Step 3 — Build both Lambda JARs

```bash
cd ../backend-aws     && mvn clean package -DskipTests
cd ../backend-ops-aws && mvn clean package -DskipTests
cd ../backend-aws-infra
```

### Step 4 — Configure Terraform variables

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

```hcl
aws_region    = "ap-southeast-1"
environment   = "dev"
project_name  = "account-posting"
support_email = "your-team@company.com"

lambda_jar_path     = "../backend-aws/target/account-posting-aws.jar"
ops_lambda_jar_path = "../backend-ops-aws/target/account-posting-ops-aws.jar"
```

> **Never commit `terraform.tfvars`** — it is in `.gitignore`.

### Step 5 — Initialise Terraform

```bash
terraform init
```

### Step 6 — Plan and apply

```bash
terraform plan
terraform apply    # ~2–3 minutes
```

Capture the API base URL:

```bash
terraform output api_gateway_url
```

### Step 7 — Confirm SNS email subscription

AWS sends a confirmation email to `support_email`. Click **Confirm subscription** — alerts will not arrive until confirmed.

### Step 8 — Seed the config table

```bash
BASE_URL=$(terraform output -raw api_gateway_url)
# e.g. https://xxxx.execute-api.ap-southeast-1.amazonaws.com/dev/v2/payment/account-posting
```

Config is managed via the **ops Lambda** (`backend-ops-aws`):

#### IMX

```bash
curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"IMX_CBS_GL","orderSeq":1,"sourceName":"IMX","targetSystem":"CBS","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"IMX_CBS_GL","orderSeq":2,"sourceName":"IMX","targetSystem":"GL","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"IMX_OBPM","orderSeq":1,"sourceName":"IMX","targetSystem":"OBPM","operation":"POSTING","processingMode":"ASYNC"}'
```

#### RMS

```bash
curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"FED_RETURN","orderSeq":1,"sourceName":"RMS","targetSystem":"CBS","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"FED_RETURN","orderSeq":2,"sourceName":"RMS","targetSystem":"GL","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"GL_RETURN","orderSeq":1,"sourceName":"RMS","targetSystem":"GL","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"MCA_RETURN","orderSeq":1,"sourceName":"RMS","targetSystem":"OBPM","operation":"POSTING","processingMode":"ASYNC"}'
```

#### STABLECOIN

```bash
curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"ADD_ACCOUNT_HOLD","orderSeq":1,"sourceName":"STABLECOIN","targetSystem":"CBS","operation":"ADD_HOLD","processingMode":"SYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"BUY_CUSTOMER_POSTING","orderSeq":1,"sourceName":"STABLECOIN","targetSystem":"CBS","operation":"REMOVE_HOLD","processingMode":"SYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"BUY_CUSTOMER_POSTING","orderSeq":2,"sourceName":"STABLECOIN","targetSystem":"CBS","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"CUSTOMER_POSTING","orderSeq":1,"sourceName":"STABLECOIN","targetSystem":"CBS","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"CUSTOMER_POSTING","orderSeq":2,"sourceName":"STABLECOIN","targetSystem":"GL","operation":"POSTING","processingMode":"ASYNC"}'
```

Verify:

```bash
curl -s "$BASE_URL/config" | jq .
```

---

## Example API calls

All paths are relative to `terraform output api_gateway_url`.

#### Create a posting (→ backend-aws)

```bash
curl -s -X POST "$BASE_URL/" \
  -H "Content-Type: application/json" \
  -d '{
    "source_name": "IMX",
    "source_reference_id": "SRC-001",
    "end_to_end_reference_id": "E2E-001",
    "request_type": "IMX_CBS_GL",
    "credit_debit_indicator": "DEBIT",
    "debtor_account": "1000123456",
    "creditor_account": "1000654321",
    "requested_execution_date": "2026-04-21",
    "amount": { "value": "5000.00", "currency_code": "USD" }
  }' | jq .
```

#### Search postings (→ backend-ops-aws)

```bash
curl -s -X POST "$BASE_URL/search" \
  -H "Content-Type: application/json" \
  -d '{"status":"PNDG","limit":20}' | jq .
```

#### Retry all pending (→ backend-ops-aws)

```bash
curl -s -X POST "$BASE_URL/retry" \
  -H "Content-Type: application/json" \
  -d '{"requested_by":"ops-team"}' | jq .
```

---

## Update Lambda after code changes

**backend-aws only:**

```bash
cd ../backend-aws && mvn clean package -DskipTests && cd ../backend-aws-infra
terraform apply -target=aws_lambda_function.main
```

**backend-ops-aws only:**

```bash
cd ../backend-ops-aws && mvn clean package -DskipTests && cd ../backend-aws-infra
terraform apply -target=aws_lambda_function.ops
```

**Both Lambdas:**

```bash
cd ../backend-aws     && mvn clean package -DskipTests
cd ../backend-ops-aws && mvn clean package -DskipTests
cd ../backend-aws-infra
terraform apply
```

---

## Destroy

```bash
terraform destroy
```

> DynamoDB tables have point-in-time recovery enabled (`dynamodb.tf`). Take a backup before destroying production data.

---

## Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `aws_region` | `ap-southeast-1` | Deployment region |
| `environment` | *(required)* | `dev`, `staging`, or `prod` — used in all resource names |
| `project_name` | `account-posting` | Resource name prefix |
| `support_email` | *(required)* | SNS failure alert recipient |
| `lambda_jar_path` | `../backend-aws/target/account-posting-aws.jar` | Path to backend-aws fat JAR |
| `ops_lambda_jar_path` | `../backend-ops-aws/target/account-posting-ops-aws.jar` | Path to backend-ops-aws fat JAR |
| `lambda_memory_mb` | `512` | Lambda memory in MB (applies to both) |
| `lambda_timeout_seconds` | `60` | Lambda max execution time |
| `sqs_batch_size` | `5` | SQS messages per backend-aws invocation |
| `sqs_visibility_timeout_seconds` | `180` | Must be greater than `lambda_timeout_seconds` |
| `dynamodb_billing_mode` | `PAY_PER_REQUEST` | `PAY_PER_REQUEST` or `PROVISIONED` |
| `dynamo_ttl_days` | `60` | Auto-delete DynamoDB items after N days |
| `log_retention_days` | `30` | CloudWatch log retention in days |

---

## Outputs Reference

```bash
terraform output api_gateway_url               # Full API base URL
terraform output lambda_function_name          # backend-aws Lambda name
terraform output lambda_arn                    # backend-aws Lambda ARN
terraform output ops_lambda_function_name      # backend-ops-aws Lambda name
terraform output ops_lambda_arn                # backend-ops-aws Lambda ARN
terraform output sqs_queue_url                 # SQS queue URL
terraform output sqs_queue_arn                 # SQS queue ARN
terraform output sns_topic_arn                 # SNS alert topic ARN
terraform output dynamodb_posting_table_name   # Posting table name
terraform output dynamodb_leg_table_name       # Leg table name
terraform output dynamodb_config_table_name    # Config table name
terraform output api_gateway_id                # API Gateway ID
```

---

## File Structure

```
backend-aws-infra/
├── docker-compose.yml          LocalStack for local dev
├── localstack/
│   ├── init.sh                 Auto-creates tables, queue, SNS topic on LocalStack start
│   ├── run-tests.ps1           Run both integration test suites (PowerShell / Windows)
│   └── run-tests.sh            Run both integration test suites (bash / Git Bash / WSL)
├── main.tf                     Provider config, locals (name_prefix)
├── variables.tf                All input variables with defaults
├── outputs.tf                  Exported values after apply
├── terraform.tfvars.example    Template — copy to terraform.tfvars and fill in values
├── dynamodb.tf                 3 tables: posting (3 GSIs + TTL + PITR), leg, config
├── sqs.tf                      Processing queue (ReportBatchItemFailures, no DLQ)
├── sns.tf                      Support alert topic + email subscription
├── iam.tf                      Lambda execution roles + DynamoDB/SQS/SNS policies
├── lambda.tf                   Both Lambda functions + SQS event source mapping + API GW permissions
├── api_gateway.tf              HTTP API v2 + route integrations per Lambda + stage
└── cloudwatch.tf               Lambda log groups + API GW log group + error alarm
```

---

## Terraform Files

### `main.tf`

Configures the AWS provider and defines a `local.name_prefix` value (`{project_name}-{environment}`)
used as the prefix for every resource name.

```hcl
locals {
  name_prefix = "${var.project_name}-${var.environment}"
}
```

Resources: none (provider + locals only).

---

### `variables.tf`

All input variables. Set required ones in `terraform.tfvars`.

| Variable | Type | Default | Required | Purpose |
|----------|------|---------|----------|---------|
| `aws_region` | string | `ap-southeast-1` | | Deployment region |
| `project_name` | string | `account-posting` | | Prefix for all resource names |
| `environment` | string | — | ✓ | `dev` / `staging` / `prod` |
| `support_email` | string | — | ✓ | SNS failure alert recipient |
| `lambda_jar_path` | string | `../backend-aws/target/...` | | Path to backend-aws fat JAR |
| `ops_lambda_jar_path` | string | `../backend-ops-aws/target/...` | | Path to backend-ops-aws fat JAR |
| `lambda_memory_mb` | number | `512` | | Memory in MB (both Lambdas) |
| `lambda_timeout_seconds` | number | `60` | | Max Lambda execution time |
| `sqs_batch_size` | number | `5` | | SQS messages per Lambda invocation |
| `sqs_visibility_timeout_seconds` | number | `180` | | Must exceed `lambda_timeout_seconds` |
| `dynamodb_billing_mode` | string | `PAY_PER_REQUEST` | | `PAY_PER_REQUEST` or `PROVISIONED` |
| `dynamo_ttl_days` | number | `60` | | Auto-expire items after N days |
| `log_retention_days` | number | `30` | | CloudWatch retention in days |

---

### `outputs.tf`

Values printed after `terraform apply` and accessible via `terraform output <name>`.

| Output | Description |
|--------|-------------|
| `api_gateway_url` | Full base URL: `https://<id>.execute-api.<region>.amazonaws.com/<env>/v2/payment/account-posting` |
| `api_gateway_id` | API Gateway resource ID |
| `lambda_function_name` | backend-aws Lambda name |
| `lambda_arn` | backend-aws Lambda ARN |
| `ops_lambda_function_name` | backend-ops-aws Lambda name |
| `ops_lambda_arn` | backend-ops-aws Lambda ARN |
| `sqs_queue_url` | SQS processing queue URL |
| `sqs_queue_arn` | SQS processing queue ARN |
| `sns_topic_arn` | SNS support alert topic ARN |
| `dynamodb_posting_table_name` | account_posting table name |
| `dynamodb_leg_table_name` | account_posting_leg table name |
| `dynamodb_config_table_name` | account_posting_config table name |

---

### `dynamodb.tf`

Three DynamoDB tables. All use `PAY_PER_REQUEST` billing by default and have TTL + PITR enabled.

#### `account_posting` (posting table)

| Key | Type | Role |
|-----|------|------|
| `postingId` (PK) | N | Primary key |

GSIs:

| Index | PK | SK | Purpose |
|-------|----|----|---------|
| `status-createdAt-index` | `status` (S) | `createdAt` (S) | Search by status + date |
| `sourceName-createdAt-index` | `sourceName` (S) | `createdAt` (S) | Search by source |
| `endToEndReferenceId-index` | `endToEndReferenceId` (S) | — | Idempotency check |

TTL attribute: `ttl` (epoch seconds).

#### `account_posting_leg` (leg table)

| Key | Type | Role |
|-----|------|------|
| `postingId` (PK) | N | Parent posting |
| `transactionOrder` (SK) | N | Leg sequence number |

TTL attribute: `ttl`.

#### `account_posting_config` (routing config table)

| Key | Type | Role |
|-----|------|------|
| `requestType` (PK) | S | Posting type (e.g. `IMX_CBS_GL`) |
| `orderSeq` (SK) | N | Execution order within type |

No TTL — config rows are permanent until deleted via the ops API.

---

### `sqs.tf`

Single SQS queue: `{name_prefix}-processing-queue`.

| Setting | Value | Why |
|---------|-------|-----|
| `visibility_timeout_seconds` | `180` | Must exceed Lambda timeout (60 s) to prevent duplicate processing |
| `message_retention_seconds` | `86400` (24 h) | Unprocessed jobs kept for 1 day |
| `receive_wait_time_seconds` | `20` | Long polling — reduces empty receives |
| No DLQ | — | `ReportBatchItemFailures` in Lambda handles partial batch failures; individual failed messages are redriven by SQS visibility timeout |

---

### `sns.tf`

SNS topic: `{name_prefix}-posting-alerts`.

Used exclusively by `backend-aws` `SqsHandler` to publish failure alerts when async leg processing fails.
`backend-ops-aws` does not publish to SNS.

An email subscription is created for `var.support_email`. AWS sends a confirmation email — click
**Confirm subscription** or alerts will not be delivered.

---

### `iam.tf`

Two IAM execution roles — one per Lambda — following least-privilege.

#### `{name_prefix}-lambda-execution-role` (backend-aws)

Attached policies:
- `AWSLambdaBasicExecutionRole` (CloudWatch Logs)
- Custom `{name_prefix}-lambda-app-policy`:
  - **DynamoDB**: full read/write on all three tables and their GSIs
  - **SQS**: `SendMessage`, `ReceiveMessage`, `DeleteMessage`, `GetQueueAttributes`, `ChangeMessageVisibility`
  - **SNS**: `Publish` on the support alert topic

#### `{name_prefix}-ops-lambda-execution-role` (backend-ops-aws)

Attached policies:
- `AWSLambdaBasicExecutionRole` (CloudWatch Logs)
- Custom `{name_prefix}-ops-lambda-app-policy`:
  - **DynamoDB**: full read/write on all three tables and their GSIs
  - **SQS**: `SendMessage`, `GetQueueAttributes` only (retry re-queuing; no consume)
  - **No SNS** — ops Lambda does not publish alerts

---

### `lambda.tf`

Two Lambda functions + SQS trigger + API Gateway invocation permissions.

#### `aws_lambda_function.main` (backend-aws)

| Setting | Value |
|---------|-------|
| Handler | `com.sr.accountposting.LambdaRequestHandler::handleRequest` |
| Runtime | `java17` |
| JAR | `var.lambda_jar_path` |
| SnapStart | `PublishedVersions` — eliminates JVM cold-start |
| Memory | `var.lambda_memory_mb` (default 512 MB) |
| Timeout | `var.lambda_timeout_seconds` (default 60 s) |
| Env vars | All six: tables, queue, SNS topic, region, TTL |

#### `aws_lambda_function.ops` (backend-ops-aws)

Same runtime/SnapStart settings. Env vars omit `SUPPORT_ALERT_TOPIC_ARN` (not needed by ops Lambda).

#### `aws_lambda_event_source_mapping.sqs_trigger`

Binds the SQS queue to **backend-aws only**. `batch_size=5`, `ReportBatchItemFailures` enabled so
a single failed message does not retry the entire batch.

#### Permissions

`aws_lambda_permission.api_gateway_invoke` and `aws_lambda_permission.ops_api_gateway_invoke` allow
API Gateway to invoke each Lambda. `source_arn` is scoped to `<api_arn>/*/*` (all stages + routes).

---

### `api_gateway.tf`

HTTP API v2 with explicit route-to-Lambda mapping. No `$default` catch-all — every route is declared.

#### `aws_apigatewayv2_api.main`

CORS pre-configured for all origins, methods, and `Content-Type` / `Authorization` headers.
Protocol: `HTTP` (not REST). Payload format: `2.0`.

#### Integrations

| Resource | Lambda |
|----------|--------|
| `aws_apigatewayv2_integration.posting_lambda` | `aws_lambda_function.main` (backend-aws) |
| `aws_apigatewayv2_integration.ops_lambda` | `aws_lambda_function.ops` (backend-ops-aws) |

#### Routes

| Route key | Integration |
|-----------|-------------|
| `POST /v2/payment/account-posting` | `posting_lambda` |
| `POST /v2/payment/account-posting/search` | `ops_lambda` |
| `POST /v2/payment/account-posting/retry` | `ops_lambda` |
| `GET /v2/payment/account-posting/{id}` | `ops_lambda` |
| `GET /v2/payment/account-posting/{id}/transaction` | `ops_lambda` |
| `GET /v2/payment/account-posting/{id}/transaction/{order}` | `ops_lambda` |
| `PATCH /v2/payment/account-posting/{id}/transaction/{order}` | `ops_lambda` |
| `GET /v2/payment/account-posting/config` | `ops_lambda` |
| `GET /v2/payment/account-posting/config/{requestType}` | `ops_lambda` |
| `POST /v2/payment/account-posting/config` | `ops_lambda` |
| `PUT /v2/payment/account-posting/config/{type}/{order}` | `ops_lambda` |
| `DELETE /v2/payment/account-posting/config/{type}/{order}` | `ops_lambda` |

#### Stage

`aws_apigatewayv2_stage.main` — named after `var.environment`, `auto_deploy = true`.
Access logs stream to CloudWatch in structured JSON (requestId, IP, method, route, status, duration).

---

### `cloudwatch.tf`

| Resource | Log group | Retention |
|----------|-----------|-----------|
| `aws_cloudwatch_log_group.main` | `/aws/lambda/{name_prefix}-handler` | `var.log_retention_days` |
| `aws_cloudwatch_log_group.ops` | `/aws/lambda/{name_prefix}-ops-handler` | `var.log_retention_days` |
| `aws_cloudwatch_log_group.api_gateway` | `/aws/apigateway/{name_prefix}-api` | `var.log_retention_days` |

Two CloudWatch metric alarms watch Lambda `Errors` (sum over 5 min, threshold=5).
Both publish to the SNS topic on breach so the support team is notified of Lambda failures.
