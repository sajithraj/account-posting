# backend-aws-infra

Terraform infrastructure for the account posting AWS implementation. This repo provisions the shared backend resources for:

- `backend-aws`: posting create API and SQS worker
- `backend-ops-aws`: search, retry, transaction-leg, and config APIs

It supports two deployment targets:

- AWS: HTTP API Gateway v2
- LocalStack: REST API Gateway v1 for local browser/Postman/UI testing

## What This Infra Creates

- 2 Lambda functions
- 3 DynamoDB tables
- 1 SQS queue
- 1 SNS topic
- API Gateway
- IAM roles and policies
- CloudWatch log groups and alarms

## High-Level Route Split

Base path:

```text
/v3/payment/account-posting
```

Routes handled by `backend-aws`:

- `POST /v3/payment/account-posting`

Routes handled by `backend-ops-aws`:

- `POST /v3/payment/account-posting/search`
- `POST /v3/payment/account-posting/retry`
- `GET /v3/payment/account-posting/{id}`
- `GET /v3/payment/account-posting/{id}/transaction`
- `GET /v3/payment/account-posting/{id}/transaction/{order}`
- `PATCH /v3/payment/account-posting/{id}/transaction/{order}`
- `GET /v3/payment/account-posting/config`
- `GET /v3/payment/account-posting/config/{requestType}`
- `POST /v3/payment/account-posting/config`
- `PUT /v3/payment/account-posting/config/{type}/{order}`
- `DELETE /v3/payment/account-posting/config/{type}/{order}`

## Important Notes

- `postingId` is now a UUID string, not a numeric sequence.
- DynamoDB `postingId` key type is `S` in both posting and leg tables.
- AWS deployment uses API Gateway HTTP API v2.
- LocalStack deployment uses API Gateway REST API v1.
- LocalStack API Gateway v1 is used because `apigatewayv2` is not supported in the current LocalStack setup used for this project.
- For Terraform-managed LocalStack deployments, set `BOOTSTRAP_AWS_RESOURCES=false` before starting LocalStack.

## Resource Naming

Most resources use:

```text
{project_name}-{environment}-...
```

Example with defaults:

```text
account-posting-dev-handler
account-posting-dev-ops-handler
account-posting-dev-api
account-posting-dev-posting
account-posting-dev-leg
account-posting-dev-config
account-posting-dev-processing-queue
```

## Prerequisites

- Docker Desktop
- Terraform 1.5+
- Java 17
- Maven 3.8+
- AWS CLI

Quick checks:

```powershell
docker --version
terraform -version
java -version
mvn -version
aws --version
```

## Local Workflows

There are two local workflows.

### 1. Integration Test Mode

Use this when you want LocalStack only for DynamoDB, SQS, and SNS, and you are fine testing through JUnit without an HTTP endpoint.

In this mode:

- `localstack/init.sh` bootstraps local AWS resources
- tests call Lambda handlers directly
- browser/Postman/UI testing is not needed

### 2. Local API Gateway Mode

Use this when you want to test the full API from:

- browser
- Postman
- UI frontend

In this mode:

- LocalStack runs Lambda, API Gateway, DynamoDB, SQS, SNS, IAM, and logs
- Terraform deploys the stack into LocalStack
- you get a localhost API base URL

This is the mode to use for config API testing from browser/Postman/UI.

## Local API Gateway Mode: Exact Steps

### Step 1. Build both Lambda jars

```powershell
cd E:\Development\code_practice\claude\project\backend-aws
mvn clean package -DskipTests

cd E:\Development\code_practice\claude\project\backend-ops-aws
mvn clean package -DskipTests
```

Expected jars:

- `E:\Development\code_practice\claude\project\backend-aws\target\account-posting-aws.jar`
- `E:\Development\code_practice\claude\project\backend-ops-aws\target\account-posting-ops-aws.jar`

### Step 2. Start LocalStack in Terraform-managed mode

From this repo:

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra

docker compose down -v
$env:BOOTSTRAP_AWS_RESOURCES = "false"
docker compose up -d
```

Why this matters:

- `BOOTSTRAP_AWS_RESOURCES=false` stops `localstack/init.sh` from pre-creating tables, queues, and topics
- Terraform becomes the single owner of the local resources

Check LocalStack health:

```powershell
docker compose ps
docker compose logs localstack
```

### Step 3. Prepare LocalStack Terraform variables

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
Copy-Item terraform.localstack.tfvars.example terraform.localstack.tfvars -Force
```

The example file is already configured for LocalStack usage, including:

- `use_localstack = true`
- `localstack_endpoint = "http://localhost:4566"`

### Step 4. Deploy to LocalStack

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
terraform init
terraform apply -var-file="terraform.localstack.tfvars" -auto-approve
```

### Step 5. Get the local API URL

```powershell
terraform output api_gateway_url
terraform output api_gateway_invoke_url
terraform output localstack_api_gateway_url
```

Use `api_gateway_url` first.

If needed, use `localstack_api_gateway_url`, which is the explicit localhost-friendly format:

```text
http://localhost:4566/restapis/<api-id>/<stage>/_user_request_/v3/payment/account-posting
```

## How To Test Config APIs

Base URL:

```text
<base-url> = terraform output api_gateway_url
```

or

```text
<base-url> = terraform output localstack_api_gateway_url
```

### Browser Testing

These are the easiest GET calls to test in the browser:

- `<base-url>/config`
- `<base-url>/config/IMX_CBS_GL`

If the browser shows an error page or raw JSON text, that is still useful. The important part is the HTTP response and payload.

### Postman Testing

Recommended order:

1. `GET <base-url>/config`
2. `GET <base-url>/config/IMX_CBS_GL`
3. `POST <base-url>/config`
4. `PUT <base-url>/config/TEST_CFG_UUID_FLOW/1`
5. `DELETE <base-url>/config/TEST_CFG_UUID_FLOW/1`

Sample create payload:

```json
{
  "request_type": "TEST_CFG_UUID_FLOW",
  "order_seq": 1,
  "source_name": "IMX",
  "target_system": "CBS",
  "operation": "POSTING",
  "processing_mode": "ASYNC"
}
```

Sample update payload:

```json
{
  "request_type": "TEST_CFG_UUID_FLOW",
  "order_seq": 1,
  "source_name": "IMX",
  "target_system": "GL",
  "operation": "POSTING",
  "processing_mode": "ASYNC"
}
```

Why use a throwaway request type:

- it avoids collisions with seeded config values
- it makes create, update, and delete easy to verify

### UI Testing

Use the same Terraform output base URL as the backend API base URL in your UI config.

For local testing, the UI should point to:

```text
http://localhost:4566/restapis/<api-id>/<stage>/_user_request_/v3/payment/account-posting
```

or whichever value `terraform output api_gateway_url` returns successfully in your environment.

## Integration Test Mode: Exact Steps

Use this if you only want test-driven validation without exposing HTTP locally.

### Step 1. Start LocalStack with bootstrap enabled

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
docker compose down -v
Remove-Item Env:BOOTSTRAP_AWS_RESOURCES -ErrorAction SilentlyContinue
docker compose up -d
```

In this mode, `localstack/init.sh` creates:

- DynamoDB tables
- SQS queue
- SNS topic

### Step 2. Run integration tests

PowerShell:

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
.\localstack\run-tests.ps1
```

Git Bash or WSL:

```bash
cd /e/Development/code_practice/claude/project/backend-aws-infra
bash localstack/run-tests.sh
```

## AWS Deployment Steps

### Step 1. Build both Lambda jars

```powershell
cd E:\Development\code_practice\claude\project\backend-aws
mvn clean package -DskipTests

cd E:\Development\code_practice\claude\project\backend-ops-aws
mvn clean package -DskipTests
```

### Step 2. Create Terraform variables

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
Copy-Item terraform.tfvars.example terraform.tfvars -Force
```

Update `terraform.tfvars` with real values.

Typical values:

```hcl
aws_region    = "ap-southeast-1"
environment   = "dev"
project_name  = "account-posting"
support_email = "your-team@company.com"

lambda_jar_path     = "../backend-aws/target/account-posting-aws.jar"
ops_lambda_jar_path = "../backend-ops-aws/target/account-posting-ops-aws.jar"
```

### Step 3. Apply Terraform

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
terraform init
terraform plan
terraform apply
```

### Step 4. Read outputs

```powershell
terraform output api_gateway_url
terraform output lambda_function_name
terraform output ops_lambda_function_name
terraform output sqs_queue_url
terraform output dynamodb_posting_table_name
terraform output dynamodb_leg_table_name
terraform output dynamodb_config_table_name
```

### Step 5. Confirm SNS email subscription

AWS sends a confirmation email to `support_email`. Alerts will not be delivered until the subscription is confirmed.

## Config Seeding Examples

Base URL:

```powershell
$BASE_URL = terraform output -raw api_gateway_url
```

Create a config row:

```powershell
Invoke-RestMethod -Method Post -Uri "$BASE_URL/config" -ContentType "application/json" -Body '{
  "request_type": "IMX_CBS_GL",
  "order_seq": 1,
  "source_name": "IMX",
  "target_system": "CBS",
  "operation": "POSTING",
  "processing_mode": "ASYNC"
}'
```

List all config:

```powershell
Invoke-RestMethod -Method Get -Uri "$BASE_URL/config"
```

Get one type:

```powershell
Invoke-RestMethod -Method Get -Uri "$BASE_URL/config/IMX_CBS_GL"
```

## Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `aws_region` | `ap-southeast-1` | Deployment region |
| `use_localstack` | `false` | Switch Terraform endpoints from AWS to LocalStack |
| `localstack_endpoint` | `http://localhost:4566` | Shared LocalStack endpoint |
| `project_name` | `account-posting` | Resource name prefix |
| `environment` | required | Environment name such as `dev`, `staging`, `prod`, or `dev-local` |
| `support_email` | required | SNS alert email recipient |
| `lambda_jar_path` | `../backend-aws/target/account-posting-aws.jar` | Main Lambda jar path |
| `ops_lambda_jar_path` | `../backend-ops-aws/target/account-posting-ops-aws.jar` | Ops Lambda jar path |
| `lambda_memory_mb` | `512` | Lambda memory for both functions |
| `lambda_timeout_seconds` | `60` | Lambda timeout |
| `sqs_batch_size` | `5` | SQS batch size for worker Lambda |
| `sqs_visibility_timeout_seconds` | `180` | SQS visibility timeout |
| `dynamodb_billing_mode` | `PAY_PER_REQUEST` | DynamoDB billing mode |
| `dynamo_ttl_days` | `60` | TTL days for posting and leg items |
| `log_retention_days` | `30` | CloudWatch log retention |

## Outputs Reference

| Output | Description |
|--------|-------------|
| `api_gateway_url` | Main base URL for the deployed API |
| `api_gateway_invoke_url` | Raw stage invoke URL |
| `localstack_api_gateway_url` | LocalStack-friendly localhost URL |
| `api_gateway_id` | API Gateway id |
| `lambda_function_name` | Main Lambda function name |
| `lambda_arn` | Main Lambda ARN |
| `ops_lambda_function_name` | Ops Lambda function name |
| `ops_lambda_arn` | Ops Lambda ARN |
| `sqs_queue_url` | Processing queue URL |
| `sqs_queue_arn` | Processing queue ARN |
| `sns_topic_arn` | SNS support alert topic ARN |
| `dynamodb_posting_table_name` | Posting table name |
| `dynamodb_leg_table_name` | Leg table name |
| `dynamodb_config_table_name` | Config table name |

## LocalStack-Specific Behavior

When `use_localstack = true`:

- provider endpoints point to `http://localhost:4566`
- Lambda SnapStart is disabled
- SNS email subscription is skipped
- CloudWatch alarms are skipped
- LocalStack REST API Gateway resources are created instead of HTTP API v2 resources

When `use_localstack = false`:

- normal AWS resources are created
- HTTP API Gateway v2 is used
- alarms and SNS email subscription are enabled

## Common Troubleshooting

### Error: `apigatewayv2 not yet implemented or pro feature`

Cause:

- LocalStack does not support the current HTTP API v2 resource path used by Terraform in this setup

Fix:

- use `terraform.localstack.tfvars` with `use_localstack = true`
- this repo will then create REST API v1 resources for LocalStack automatically

### Browser or Postman cannot resolve the returned URL

Try:

- `terraform output localstack_api_gateway_url`

instead of:

- `terraform output api_gateway_url`

### Terraform apply fails because resources already exist

Usually this means LocalStack bootstrap and Terraform are both trying to manage the same resources.

Fix:

```powershell
cd E:\Development\code_practice\claude\project\backend-aws-infra
docker compose down -v
$env:BOOTSTRAP_AWS_RESOURCES = "false"
docker compose up -d
terraform apply -var-file="terraform.localstack.tfvars" -auto-approve
```

### Config APIs return unexpected routing behavior

The ops handler route ordering was fixed so that:

- `GET /config`
- `GET /config/{requestType}`

are not mistaken for:

- `GET /{postingId}`

## Useful Commands

Start LocalStack:

```powershell
docker compose up -d
```

Stop LocalStack:

```powershell
docker compose down
```

Wipe LocalStack data:

```powershell
docker compose down -v
```

Validate Terraform:

```powershell
terraform validate
```

Destroy AWS or LocalStack-managed Terraform resources:

```powershell
terraform destroy
```

## Repo Files

- `docker-compose.yml`: LocalStack services
- `localstack/init.sh`: bootstrap script for integration-test mode
- `localstack/run-tests.ps1`: Windows integration test runner
- `localstack/run-tests.sh`: bash integration test runner
- `main.tf`: provider configuration and shared locals
- `variables.tf`: input variables
- `outputs.tf`: Terraform outputs
- `api_gateway.tf`: AWS HTTP API v2 and LocalStack REST API v1 definitions
- `lambda.tf`: Lambda functions, env vars, SQS event source mapping, invoke permissions
- `dynamodb.tf`: posting, leg, and config tables
- `sqs.tf`: processing queue
- `sns.tf`: support alert topic
- `iam.tf`: execution roles and policies
- `cloudwatch.tf`: log groups and alarms

