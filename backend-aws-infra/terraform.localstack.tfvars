use_localstack = true
aws_region = "ap-southeast-1"
environment = "dev-local"
project_name = "account-posting"
localstack_endpoint = "http://localhost:4566"

lambda_jar_path = "../backend-aws/target/account-posting-aws.jar"
ops_lambda_jar_path = "../backend-ops-aws/target/account-posting-ops-aws.jar"

support_email = "localstack@example.com"

lambda_memory_mb = 512
lambda_timeout_seconds = 60
sqs_visibility_timeout_seconds = 180
sqs_batch_size = 5
dynamodb_billing_mode = "PAY_PER_REQUEST"
dynamo_ttl_days = 60
log_retention_days = 30
