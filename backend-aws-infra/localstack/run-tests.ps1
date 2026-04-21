# Run LocalStack integration tests for both backend-aws and backend-ops-aws
# Usage: from backend-aws-infra directory: .\localstack\run-tests.ps1

$env:AWS_ACCESS_KEY_ID          = "test"
$env:AWS_SECRET_ACCESS_KEY      = "test"
$env:AWS_ENDPOINT_URL           = "http://localhost:4566"
$env:AWS_ACCOUNT_REGION         = "ap-southeast-1"
$env:POSTING_TABLE_NAME         = "account-posting"
$env:LEG_TABLE_NAME             = "account-posting-leg"
$env:CONFIG_TABLE_NAME          = "account-posting-config"
$env:PROCESSING_QUEUE_URL       = "http://localhost:4566/000000000000/posting-queue"
$env:SUPPORT_ALERT_TOPIC_ARN    = "arn:aws:sns:ap-southeast-1:000000000000:posting-alerts"

Write-Host "==> Running backend-aws integration tests"
Set-Location "$PSScriptRoot\..\..\backend-aws"
mvn test "-Plocalstack"

Write-Host "==> Running backend-ops-aws integration tests"
Set-Location "$PSScriptRoot\..\..\backend-ops-aws"
mvn test "-Plocalstack"

Write-Host "==> All integration tests passed"
