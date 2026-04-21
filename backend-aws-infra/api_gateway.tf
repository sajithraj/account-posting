resource "aws_apigatewayv2_api" "main" {
  name          = "${local.name_prefix}-api"
  protocol_type = "HTTP"
  description   = "Account Posting Orchestrator API"

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization", "X-Requested-With"]
    max_age       = 300
  }

  tags = {
    Name = "${local.name_prefix}-api"
  }
}

# Integration: backend-aws (posting creation + SQS consumer)
resource "aws_apigatewayv2_integration" "posting_lambda" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.main.invoke_arn
  payload_format_version = "2.0"
}

# Integration: backend-ops-aws (ops dashboard)
resource "aws_apigatewayv2_integration" "ops_lambda" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.ops.invoke_arn
  payload_format_version = "2.0"
}

# POST /v3/payment/account-posting → backend-aws (create posting)
resource "aws_apigatewayv2_route" "create_posting" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /v3/payment/account-posting"
  target    = "integrations/${aws_apigatewayv2_integration.posting_lambda.id}"
}

# POST /v3/payment/account-posting/search → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_search" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /v3/payment/account-posting/search"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# POST /v3/payment/account-posting/retry → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_retry" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /v3/payment/account-posting/retry"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# GET /v3/payment/account-posting/{id} → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_get_posting" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /v3/payment/account-posting/{id}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# GET /v3/payment/account-posting/{id}/transaction → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_list_legs" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /v3/payment/account-posting/{id}/transaction"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# GET /v3/payment/account-posting/{id}/transaction/{order} → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_get_leg" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /v3/payment/account-posting/{id}/transaction/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# PATCH /v3/payment/account-posting/{id}/transaction/{order} → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_patch_leg" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "PATCH /v3/payment/account-posting/{id}/transaction/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# GET /v3/payment/account-posting/config → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_list_config" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /v3/payment/account-posting/config"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# GET /v3/payment/account-posting/config/{requestType} → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_get_config_by_type" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /v3/payment/account-posting/config/{requestType}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# POST /v3/payment/account-posting/config → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_create_config" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /v3/payment/account-posting/config"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# PUT /v3/payment/account-posting/config/{type}/{order} → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_update_config" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "PUT /v3/payment/account-posting/config/{type}/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

# DELETE /v3/payment/account-posting/config/{type}/{order} → backend-ops-aws
resource "aws_apigatewayv2_route" "ops_delete_config" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "DELETE /v3/payment/account-posting/config/{type}/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda.id}"
}

resource "aws_apigatewayv2_stage" "main" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = var.environment
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId               = "$context.requestId"
      sourceIp                = "$context.identity.sourceIp"
      requestTime             = "$context.requestTime"
      httpMethod              = "$context.httpMethod"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
      protocol                = "$context.protocol"
      responseLength          = "$context.responseLength"
      integrationErrorMessage = "$context.integrationErrorMessage"
    })
  }

  tags = {
    Name = "${local.name_prefix}-stage"
  }
}
