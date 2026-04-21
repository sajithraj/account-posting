variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "ap-southeast-1"
}

variable "use_localstack" {
  description = "Use LocalStack endpoints instead of real AWS"
  type        = bool
  default     = false
}

variable "localstack_endpoint" {
  description = "Base endpoint for LocalStack services"
  type        = string
  default     = "http://localhost:4566"
}

variable "project_name" {
  description = "Project name used as resource name prefix"
  type        = string
  default     = "account-posting"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "lambda_jar_path" {
  description = "Path to the compiled backend-aws Lambda fat JAR (relative to infra folder)"
  type        = string
  default     = "../backend-aws/target/account-posting-aws.jar"
}

variable "ops_lambda_jar_path" {
  description = "Path to the compiled backend-ops-aws Lambda fat JAR (relative to infra folder)"
  type        = string
  default     = "../backend-ops-aws/target/account-posting-ops-aws.jar"
}

variable "support_email" {
  description = "Email address for SNS support alert notifications"
  type        = string
}

variable "lambda_memory_mb" {
  description = "Lambda memory allocation in MB"
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 60
}

variable "sqs_visibility_timeout_seconds" {
  description = "SQS message visibility timeout in seconds"
  type        = number
  default     = 180
}

variable "sqs_batch_size" {
  description = "Number of SQS messages per Lambda invocation"
  type        = number
  default     = 5
}

variable "dynamodb_billing_mode" {
  description = "DynamoDB billing mode: PAY_PER_REQUEST or PROVISIONED"
  type        = string
  default     = "PAY_PER_REQUEST"
}

variable "dynamo_ttl_days" {
  description = "DynamoDB item TTL in days"
  type        = number
  default     = 60
}

variable "log_retention_days" {
  description = "CloudWatch log group retention in days"
  type        = number
  default     = 30
}
