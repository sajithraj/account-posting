# ─────────────────────────────────────────────
# Shared environment variables
# ─────────────────────────────────────────────
locals {
  lambda_env_vars = {
    ENVIRONMENT             = var.environment
    AWS_ACCOUNT_REGION      = var.aws_region
    POSTING_TABLE_NAME      = aws_dynamodb_table.account_posting.name
    LEG_TABLE_NAME          = aws_dynamodb_table.account_posting_leg.name
    CONFIG_TABLE_NAME       = aws_dynamodb_table.account_posting_config.name
    PROCESSING_QUEUE_URL    = aws_sqs_queue.processing_queue.url
    SUPPORT_ALERT_TOPIC_ARN = aws_sns_topic.support_alert.arn
    DYNAMO_TTL_DAYS         = tostring(var.dynamo_ttl_days)
  }
}

# ─────────────────────────────────────────────
# Single Lambda — handles both API Gateway and SQS events.
# UnifiedHandler detects the input type and delegates:
#   API Gateway → ApiGatewayHandler
#   SQS         → SqsHandler
# ─────────────────────────────────────────────
resource "aws_lambda_function" "main" {
  function_name    = "${local.name_prefix}-handler"
  role             = aws_iam_role.lambda_execution.arn
  handler          = "com.sr.accountposting.LambdaRequestHandler::handleRequest"
  runtime          = "java17"
  filename         = var.lambda_jar_path
  source_code_hash = fileexists(var.lambda_jar_path) ? filebase64sha256(var.lambda_jar_path) : null
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = local.lambda_env_vars
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic_execution,
    aws_iam_role_policy_attachment.lambda_app_policy_attach,
    aws_cloudwatch_log_group.main
  ]

  tags = {
    Name = "${local.name_prefix}-handler"
  }
}

# ─────────────────────────────────────────────
# SQS → Lambda trigger
# batch_size=5, max_receive_count=1 (no auto-retry)
# ─────────────────────────────────────────────
resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn                   = aws_sqs_queue.processing_queue.arn
  function_name                      = aws_lambda_function.main.arn
  batch_size                         = var.sqs_batch_size
  maximum_batching_window_in_seconds = 0
  function_response_types            = ["ReportBatchItemFailures"]

  depends_on = [aws_lambda_function.main]
}

# Allow API Gateway to invoke the Lambda
resource "aws_lambda_permission" "api_gateway_invoke" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.main.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}
