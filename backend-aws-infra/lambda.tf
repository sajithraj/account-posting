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

  ops_lambda_env_vars = {
    ENVIRONMENT          = var.environment
    AWS_ACCOUNT_REGION   = var.aws_region
    POSTING_TABLE_NAME   = aws_dynamodb_table.account_posting.name
    LEG_TABLE_NAME       = aws_dynamodb_table.account_posting_leg.name
    CONFIG_TABLE_NAME    = aws_dynamodb_table.account_posting_config.name
    PROCESSING_QUEUE_URL = aws_sqs_queue.processing_queue.url
    DYNAMO_TTL_DAYS      = tostring(var.dynamo_ttl_days)
  }
}

# backend-aws — posting creation (POST /) and SQS consumer
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

# backend-ops-aws — ops dashboard (search, retry, legs, config CRUD)
resource "aws_lambda_function" "ops" {
  function_name    = "${local.name_prefix}-ops-handler"
  role             = aws_iam_role.ops_lambda_execution.arn
  handler          = "com.sr.accountposting.LambdaRequestHandler::handleRequest"
  runtime          = "java17"
  filename         = var.ops_lambda_jar_path
  source_code_hash = fileexists(var.ops_lambda_jar_path) ? filebase64sha256(var.ops_lambda_jar_path) : null
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = local.ops_lambda_env_vars
  }

  depends_on = [
    aws_iam_role_policy_attachment.ops_lambda_basic_execution,
    aws_iam_role_policy_attachment.ops_lambda_app_policy_attach,
    aws_cloudwatch_log_group.ops
  ]

  tags = {
    Name = "${local.name_prefix}-ops-handler"
  }
}

# SQS → backend-aws trigger (batch_size=5, ReportBatchItemFailures)
resource "aws_lambda_event_source_mapping" "sqs_trigger" {
  event_source_arn                   = aws_sqs_queue.processing_queue.arn
  function_name                      = aws_lambda_function.main.arn
  batch_size                         = var.sqs_batch_size
  maximum_batching_window_in_seconds = 0
  function_response_types            = ["ReportBatchItemFailures"]

  depends_on = [aws_lambda_function.main]
}

resource "aws_lambda_permission" "api_gateway_invoke" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.main.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}

resource "aws_lambda_permission" "ops_api_gateway_invoke" {
  statement_id  = "AllowAPIGatewayInvokeOps"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ops.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}
