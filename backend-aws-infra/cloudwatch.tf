resource "aws_cloudwatch_log_group" "main" {
  name              = "/aws/lambda/${local.name_prefix}-handler"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-handler-logs"
  }
}

resource "aws_cloudwatch_log_group" "ops" {
  name              = "/aws/lambda/${local.name_prefix}-ops-handler"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-ops-handler-logs"
  }
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${local.name_prefix}-api"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-api-logs"
  }
}

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  count = var.use_localstack ? 0 : 1

  alarm_name          = "${local.name_prefix}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Posting-creation Lambda error rate exceeded 5 in 5 minutes"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.main.function_name
  }

  alarm_actions = [aws_sns_topic.support_alert.arn]

  tags = {
    Name = "${local.name_prefix}-lambda-error-alarm"
  }
}

resource "aws_cloudwatch_metric_alarm" "ops_lambda_errors" {
  count = var.use_localstack ? 0 : 1

  alarm_name          = "${local.name_prefix}-ops-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Ops-dashboard Lambda error rate exceeded 5 in 5 minutes"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.ops.function_name
  }

  alarm_actions = [aws_sns_topic.support_alert.arn]

  tags = {
    Name = "${local.name_prefix}-ops-lambda-error-alarm"
  }
}
