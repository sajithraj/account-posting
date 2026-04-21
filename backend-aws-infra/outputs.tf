output "api_gateway_url" {
  description = "Base URL of the API Gateway endpoint"
  value       = "${aws_apigatewayv2_stage.main.invoke_url}/v2/payment/account-posting"
}

output "api_gateway_id" {
  description = "API Gateway ID"
  value       = aws_apigatewayv2_api.main.id
}

output "lambda_arn" {
  description = "ARN of the posting-creation Lambda (backend-aws)"
  value       = aws_lambda_function.main.arn
}

output "lambda_function_name" {
  description = "Name of the posting-creation Lambda (backend-aws)"
  value       = aws_lambda_function.main.function_name
}

output "ops_lambda_arn" {
  description = "ARN of the ops-dashboard Lambda (backend-ops-aws)"
  value       = aws_lambda_function.ops.arn
}

output "ops_lambda_function_name" {
  description = "Name of the ops-dashboard Lambda (backend-ops-aws)"
  value       = aws_lambda_function.ops.function_name
}

output "sqs_queue_url" {
  description = "URL of the SQS processing queue"
  value       = aws_sqs_queue.processing_queue.url
}

output "sqs_queue_arn" {
  description = "ARN of the SQS processing queue"
  value       = aws_sqs_queue.processing_queue.arn
}

output "sns_topic_arn" {
  description = "ARN of the SNS support alert topic"
  value       = aws_sns_topic.support_alert.arn
}

output "dynamodb_posting_table_name" {
  description = "Name of the account_posting DynamoDB table"
  value       = aws_dynamodb_table.account_posting.name
}

output "dynamodb_leg_table_name" {
  description = "Name of the account_posting_leg DynamoDB table"
  value       = aws_dynamodb_table.account_posting_leg.name
}

output "dynamodb_config_table_name" {
  description = "Name of the account_posting_config DynamoDB table"
  value       = aws_dynamodb_table.account_posting_config.name
}
