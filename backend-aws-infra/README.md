# Account Posting — AWS Infrastructure (`backend-aws-infra`)

Terraform configuration for the full AWS deployment. Provisions one Lambda function (handles both API Gateway and SQS events), three DynamoDB tables, SQS queue, SNS alert topic, HTTP API Gateway, IAM role, and CloudWatch alarms.

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

## AWS Resources Provisioned

| Resource | Name pattern | Detail |
|----------|-------------|--------|
| Lambda | `{project}-{env}-handler` | Java 17, SnapStart, handles both API and SQS |
| API Gateway | `{project}-{env}-api` | HTTP API v2, `$default` route → Lambda |
| SQS Queue | `{project}-{env}-processing-queue` | Async posting jobs, no DLQ |
| DynamoDB | `{project}-{env}-posting` | PK=postingId, 3 GSIs, TTL 60 days |
| DynamoDB | `{project}-{env}-leg` | PK=postingId + SK=transactionOrder, TTL |
| DynamoDB | `{project}-{env}-config` | PK=requestType + SK=orderSeq — routing rules + processingMode |
| SNS Topic | `{project}-{env}-support-alert` | Email alert on async posting failures |
| IAM Role | `{project}-{env}-lambda-execution-role` | DynamoDB + SQS + SNS least-privilege policy |
| CloudWatch | `/aws/lambda/{project}-{env}-handler` | Lambda log group + error alarm |
| CloudWatch | `/aws/apigateway/{project}-{env}-api` | API Gateway access log group |

---

## Part 1 — Local Development with LocalStack

Use this to test the full flow on your machine before touching AWS.

### Step 1 — Start LocalStack

`docker-compose.yml` and `localstack/init.sh` are included in this folder. The init script runs automatically when LocalStack is ready and creates all DynamoDB tables (with GSIs), the SQS queue, and the SNS topic.

> **Windows only — fix line endings first.**
> The init script must have Unix line endings (LF). Run once after cloning:
> ```bash
> git config core.autocrlf false
> git add --renormalize localstack/init.sh
> ```
> Or open `localstack/init.sh` in VS Code, click **CRLF** in the bottom-right corner, select **LF**, and save.

```bash
cd backend-aws-infra
docker compose up -d
```

---

### Step 2 — Confirm LocalStack is ready

```bash
docker compose ps
```

`STATUS` should show `healthy`. Then check the init logs:

```bash
docker compose logs localstack
```

Expected output at the end:

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

Optionally verify with the AWS CLI:

```bash
aws --endpoint-url=http://localhost:4566 --region ap-southeast-1 dynamodb list-tables
aws --endpoint-url=http://localhost:4566 --region ap-southeast-1 sqs list-queues
```

---

### Step 3 — Build the Lambda JAR

```bash
cd ../backend-aws
mvn clean package -DskipTests
cd ../backend-aws-infra
```

Confirm it built:

```bash
ls ../backend-aws/target/account-posting-aws.jar
```

---

### Step 4 — Set environment variables

Export these before starting the app locally. These tell the AWS SDK to hit LocalStack instead of real AWS.

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

`AWS_ENDPOINT_URL` is picked up automatically by AWS SDK Java v2 (2.21+) — no code change needed.

---

### Step 5 — Run the integration tests

The integration test (`LocalStackIntegrationTest`) directly instantiates the Lambda handler and drives it with simulated API Gateway and SQS events — no Lambda deployment or separate server needed. It covers all scenarios in order: seed config → async posting flow (including SQS processing) → sync posting flow → search → validation errors → retry.

**PowerShell (Windows):**

```powershell
cd backend-aws-infra
.\localstack\run-tests.ps1
```

**Git Bash / WSL:**

```bash
cd backend-aws-infra
bash localstack/run-tests.sh
```

The scripts set all required env vars and run only the `LocalStackIntegrationTest` class. Normal `mvn test` runs skip it.

---

### What the tests cover

| Order | Test | What it validates |
|-------|------|-----------------|
| 1–2 | `seedConfig_*` | POST /config seeds IMX_CBS_GL (async) and ADD_ACCOUNT_HOLD (sync) |
| 3–4 | `getConfig_*` | GET /config and /config/{requestType} return seeded rows |
| 5 | `createPosting_async_*` | POST / with IMX_CBS_GL returns ACSP immediately and queues SQS message |
| 6 | `createPosting_duplicate_*` | Duplicate end_to_end_reference_id returns 422 |
| 7 | `processAsyncPosting_viaSqsEvent` | Reads queued message from SQS, invokes handler as SQS event — fetches configs from DynamoDB, creates legs, runs CBS+GL stubs, marks posting ACSP |
| 8 | `createPosting_sync_*` | POST / with ADD_ACCOUNT_HOLD processes inline, returns final status, no SQS message queued |
| 9–10 | `search_*` | POST /search by status and sourceName |
| 11–12 | `createPosting_unknown/missing_*` | Unknown requestType → 400, missing amount → 400 |
| 13 | `retry_*` | POST /retry queues PNDG/RECEIVED postings and returns counts |

---

### Step 6 — Seed config into LocalStack (manual / CLI alternative)

If you prefer seeding via CLI rather than the test, use `dynamodb put-item` directly:

```bash
LS="http://localhost:4566"

# IMX_CBS_GL — async
aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"IMX_CBS_GL"},"orderSeq":{"N":"1"},"sourceName":{"S":"IMX"},"targetSystem":{"S":"CBS"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"IMX_CBS_GL"},"orderSeq":{"N":"2"},"sourceName":{"S":"IMX"},"targetSystem":{"S":"GL"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"IMX_OBPM"},"orderSeq":{"N":"1"},"sourceName":{"S":"IMX"},"targetSystem":{"S":"OBPM"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

# FED_RETURN — async
aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"FED_RETURN"},"orderSeq":{"N":"1"},"sourceName":{"S":"RMS"},"targetSystem":{"S":"CBS"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"FED_RETURN"},"orderSeq":{"N":"2"},"sourceName":{"S":"RMS"},"targetSystem":{"S":"GL"},"operation":{"S":"POSTING"},"processingMode":{"S":"ASYNC"}}'

# ADD_ACCOUNT_HOLD — sync
aws --endpoint-url=$LS --region ap-southeast-1 dynamodb put-item --table-name account-posting-config \
  --item '{"requestType":{"S":"ADD_ACCOUNT_HOLD"},"orderSeq":{"N":"1"},"sourceName":{"S":"STABLECOIN"},"targetSystem":{"S":"CBS"},"operation":{"S":"ADD_HOLD"},"processingMode":{"S":"SYNC"}}'
```

---

### Docker Compose lifecycle

| Command | What it does |
|---------|-------------|
| `docker compose up -d` | Start in background |
| `docker compose ps` | Show status and health |
| `docker compose logs -f localstack` | Stream logs |
| `docker compose stop` | Pause — data is preserved in the named volume |
| `docker compose start` | Resume a stopped container |
| `docker compose restart` | Restart and re-run init (skips existing resources) |
| `docker compose down` | Stop and remove container — **volume (data) is kept** |
| `docker compose down -v` | Stop, remove container **and wipe all data** — next `up` starts fresh |

---

## Part 2 — AWS Deploy

### Step 1 — Clear LocalStack environment variables

If you followed Part 1, your shell has `AWS_ACCESS_KEY_ID=test` and `AWS_SECRET_ACCESS_KEY=test` set. Terraform uses the same credential chain as the AWS CLI — those fake values will cause a `InvalidClientTokenId` 403 error against real AWS. Unset them first:

```bash
unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
unset AWS_ENDPOINT_URL
unset AWS_ACCOUNT_REGION
unset POSTING_TABLE_NAME
unset LEG_TABLE_NAME
unset CONFIG_TABLE_NAME
unset PROCESSING_QUEUE_URL
unset SUPPORT_ALERT_TOPIC_ARN
```

---

### Step 2 — Configure AWS credentials

Terraform uses the standard AWS credential chain. Pick one method:

**Option A — AWS CLI (recommended for local dev)**

```bash
aws configure
```

Enter your `AWS Access Key ID`, `AWS Secret Access Key`, region (`ap-southeast-1`), and output format (`json`). Credentials are saved to `~/.aws/credentials`.

Verify it works:

```bash
aws sts get-caller-identity
```

You should see your account ID and IAM user/role ARN. If this command succeeds, `terraform plan` will too.

**Option B — Environment variables (CI / temporary)**

```bash
export AWS_ACCESS_KEY_ID=<your-real-key>
export AWS_SECRET_ACCESS_KEY=<your-real-secret>
export AWS_DEFAULT_REGION=ap-southeast-1
```

**Option C — AWS SSO / named profile**

```bash
aws sso login --profile my-profile
export AWS_PROFILE=my-profile
```

---

### Step 3 — Build the Lambda JAR

```bash
cd ../backend-aws
mvn clean package -DskipTests
cd ../backend-aws-infra
```

If you already built in Part 1, skip this.

---

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

lambda_jar_path = "../backend-aws/target/account-posting-aws.jar"
```

> **Never commit `terraform.tfvars`** — add it to `.gitignore`.

---

### Step 5 — Initialise Terraform

Downloads the AWS provider plugin. Run once per machine:

```bash
terraform init
```

---

### Step 6 — Plan and apply

```bash
# Preview all resources that will be created
terraform plan

# Create all resources (~2–3 minutes)
terraform apply
```

Capture the API base URL after apply:

```bash
terraform output api_gateway_url
```

---

### Step 7 — Confirm SNS email subscription

AWS sends a confirmation email to `support_email`. Open it and click **Confirm subscription** — failure alerts will not arrive until confirmed.

---

### Step 8 — Seed the config table

```bash
BASE_URL=$(terraform output -raw api_gateway_url)
# e.g. https://xxxx.execute-api.ap-southeast-1.amazonaws.com/dev/v2/payment/account-posting
```

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
  '{"requestType":"GL_RETURN","orderSeq":2,"sourceName":"RMS","targetSystem":"GL","operation":"POSTING","processingMode":"ASYNC"}'

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
  '{"requestType":"BUY_CUSTOMER_POSTNG","orderSeq":2,"sourceName":"STABLECOIN","targetSystem":"CBS","operation":"POSTING","processingMode":"ASYNC"}'

curl -s -X POST "$BASE_URL/config" -H "Content-Type: application/json" -d \
  '{"requestType":"BUY_CUSTOMER_POSTNG","orderSeq":3,"sourceName":"STABLECOIN","targetSystem":"GL","operation":"POSTING","processingMode":"ASYNC"}'

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

## API Endpoints

All paths are relative to the base URL (from `terraform output api_gateway_url` for AWS, or your local app URL for LocalStack).

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/` | Create a new posting |
| `GET` | `/{postingId}` | Get posting by ID |
| `POST` | `/search` | Search postings (status, sourceName, date range) |
| `POST` | `/retry` | Retry PNDG / RECEIVED postings |
| `GET` | `/{postingId}/transaction` | List all legs for a posting |
| `GET` | `/{postingId}/transaction/{order}` | Get a single leg |
| `PATCH` | `/{postingId}/transaction/{order}` | Manual leg status override |
| `GET` | `/config` | List all config rows |
| `GET` | `/config/{requestType}` | Get config rows for a request type |
| `POST` | `/config` | Add a config row |
| `PUT` | `/config/{requestType}/{orderSeq}` | Update a config row |
| `DELETE` | `/config/{requestType}/{orderSeq}` | Delete a config row |

#### Create a posting

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

#### Search by status

```bash
curl -s -X POST "$BASE_URL/search" \
  -H "Content-Type: application/json" \
  -d '{"status":"PNDG","limit":20}' | jq .
```

#### Retry all pending

```bash
curl -s -X POST "$BASE_URL/retry" \
  -H "Content-Type: application/json" \
  -d '{"requested_by":"ops-team"}' | jq .
```

---

## Update Lambda After Code Changes

```bash
cd ../backend-aws
mvn clean package -DskipTests
cd ../backend-aws-infra
terraform apply -target=aws_lambda_function.main
```

---

## Destroy

```bash
terraform destroy
```

> DynamoDB tables are destroyed immediately. Point-in-time recovery is enabled in `dynamodb.tf` — take a backup before destroying production.

---

## Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `aws_region` | `ap-southeast-1` | Deployment region |
| `environment` | *(required)* | `dev`, `staging`, or `prod` — used in all resource names |
| `project_name` | `account-posting` | Resource name prefix |
| `support_email` | *(required)* | SNS alert recipient |
| `lambda_jar_path` | `../backend-aws/target/account-posting-aws.jar` | Path to fat JAR |
| `lambda_memory_mb` | `512` | Lambda memory in MB |
| `lambda_timeout_seconds` | `60` | Lambda max execution time |
| `sqs_batch_size` | `5` | SQS messages per Lambda invocation |
| `sqs_visibility_timeout_seconds` | `180` | Must be greater than `lambda_timeout_seconds` |
| `dynamodb_billing_mode` | `PAY_PER_REQUEST` | `PAY_PER_REQUEST` or `PROVISIONED` |
| `dynamo_ttl_days` | `60` | Auto-delete items after N days |
| `log_retention_days` | `30` | CloudWatch log retention in days |

---

## Outputs Reference

```bash
terraform output api_gateway_url              # Full API base URL (includes /v2/payment/account-posting)
terraform output lambda_function_name         # Lambda function name
terraform output lambda_arn                   # Lambda ARN
terraform output sqs_queue_url                # SQS queue URL
terraform output sqs_queue_arn                # SQS queue ARN
terraform output sns_topic_arn                # SNS topic ARN
terraform output dynamodb_posting_table_name  # Posting table name
terraform output dynamodb_leg_table_name      # Leg table name
terraform output dynamodb_config_table_name   # Config table name
terraform output api_gateway_id               # API Gateway ID
```

---

## File Structure

```
backend-aws-infra/
├── docker-compose.yml         LocalStack for local dev
├── localstack/
│   ├── init.sh                Auto-creates tables, queue, SNS topic on LocalStack start
│   ├── run-tests.ps1          Run integration tests (PowerShell / Windows)
│   └── run-tests.sh           Run integration tests (bash / Git Bash / WSL)
├── main.tf                    Provider config, locals (name_prefix)
├── variables.tf               All input variables with defaults
├── outputs.tf                 Exported values after apply
├── terraform.tfvars.example   Template — copy to terraform.tfvars
├── dynamodb.tf                3 tables: posting (3 GSIs), leg, config
├── sqs.tf                     Processing queue (no DLQ)
├── sns.tf                     Support alert topic + email subscription
├── iam.tf                     Lambda execution role + DynamoDB/SQS/SNS policy
├── lambda.tf                  Lambda function + SQS event source mapping + API GW permission
├── api_gateway.tf             HTTP API v2 + $default route + stage
└── cloudwatch.tf              Lambda log group + API GW log group + error alarm
```
