resource "aws_iam_role" "lambda_execution" {
  name = "${local.name_prefix}-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = { Service = "lambda.amazonaws.com" }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-lambda-execution-role"
  }
}

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "lambda_app_policy" {
  name        = "${local.name_prefix}-lambda-app-policy"
  description = "DynamoDB + SQS + SNS permissions for the posting-creation Lambda"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:BatchGetItem",
          "dynamodb:BatchWriteItem",
          "dynamodb:ConditionCheckItem"
        ]
        Resource = [
          aws_dynamodb_table.account_posting.arn,
          "${aws_dynamodb_table.account_posting.arn}/index/*",
          aws_dynamodb_table.account_posting_leg.arn,
          "${aws_dynamodb_table.account_posting_leg.arn}/index/*",
          aws_dynamodb_table.account_posting_config.arn,
          "${aws_dynamodb_table.account_posting_config.arn}/index/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = [aws_sqs_queue.processing_queue.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = [aws_sns_topic.support_alert.arn]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_app_policy_attach" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = aws_iam_policy.lambda_app_policy.arn
}

# ─── Ops Lambda (backend-ops-aws) — no SNS ───────────────────────────────────

resource "aws_iam_role" "ops_lambda_execution" {
  name = "${local.name_prefix}-ops-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = { Service = "lambda.amazonaws.com" }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-ops-lambda-execution-role"
  }
}

resource "aws_iam_role_policy_attachment" "ops_lambda_basic_execution" {
  role       = aws_iam_role.ops_lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "ops_lambda_app_policy" {
  name        = "${local.name_prefix}-ops-lambda-app-policy"
  description = "DynamoDB + SQS permissions for the ops-dashboard Lambda (no SNS)"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:BatchGetItem",
          "dynamodb:BatchWriteItem",
          "dynamodb:ConditionCheckItem"
        ]
        Resource = [
          aws_dynamodb_table.account_posting.arn,
          "${aws_dynamodb_table.account_posting.arn}/index/*",
          aws_dynamodb_table.account_posting_leg.arn,
          "${aws_dynamodb_table.account_posting_leg.arn}/index/*",
          aws_dynamodb_table.account_posting_config.arn,
          "${aws_dynamodb_table.account_posting_config.arn}/index/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = [aws_sqs_queue.processing_queue.arn]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ops_lambda_app_policy_attach" {
  role       = aws_iam_role.ops_lambda_execution.name
  policy_arn = aws_iam_policy.ops_lambda_app_policy.arn
}
