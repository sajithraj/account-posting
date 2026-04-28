resource "aws_apigatewayv2_api" "main" {
  count         = var.use_localstack ? 0 : 1
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

resource "aws_apigatewayv2_integration" "posting_lambda" {
  count                  = var.use_localstack ? 0 : 1
  api_id                 = aws_apigatewayv2_api.main[0].id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.main.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_integration" "ops_lambda" {
  count                  = var.use_localstack ? 0 : 1
  api_id                 = aws_apigatewayv2_api.main[0].id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.ops.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "create_posting" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "POST /v3/payment/account-posting"
  target    = "integrations/${aws_apigatewayv2_integration.posting_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_search" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "POST /v3/payment/account-posting/search"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_search_get" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "GET /v3/payment/account-posting/search"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_retry" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "POST /v3/payment/account-posting/retry"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_get_posting" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "GET /v3/payment/account-posting/{id}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_list_legs" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "GET /v3/payment/account-posting/{id}/transaction"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_get_leg" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "GET /v3/payment/account-posting/{id}/transaction/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_patch_leg" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "PATCH /v3/payment/account-posting/{id}/transaction/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_list_config" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "GET /v3/payment/account-posting/config"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_get_config_by_type" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "GET /v3/payment/account-posting/config/{requestType}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_create_config" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "POST /v3/payment/account-posting/config"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_update_config" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "PUT /v3/payment/account-posting/config/{type}/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_route" "ops_delete_config" {
  count     = var.use_localstack ? 0 : 1
  api_id    = aws_apigatewayv2_api.main[0].id
  route_key = "DELETE /v3/payment/account-posting/config/{type}/{order}"
  target    = "integrations/${aws_apigatewayv2_integration.ops_lambda[0].id}"
}

resource "aws_apigatewayv2_stage" "main" {
  count       = var.use_localstack ? 0 : 1
  api_id      = aws_apigatewayv2_api.main[0].id
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

resource "aws_api_gateway_rest_api" "localstack" {
  count       = var.use_localstack ? 1 : 0
  name        = "${local.name_prefix}-api"
  description = "Account Posting Orchestrator API (LocalStack REST API)"
}

resource "aws_api_gateway_resource" "localstack_v3" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_rest_api.localstack[0].root_resource_id
  path_part   = "v3"
}

resource "aws_api_gateway_resource" "localstack_payment" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_v3[0].id
  path_part   = "payment"
}

resource "aws_api_gateway_resource" "localstack_account_posting" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_payment[0].id
  path_part   = "account-posting"
}

resource "aws_api_gateway_resource" "localstack_search" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_account_posting[0].id
  path_part   = "search"
}

resource "aws_api_gateway_resource" "localstack_retry" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_account_posting[0].id
  path_part   = "retry"
}

resource "aws_api_gateway_resource" "localstack_posting_id" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_account_posting[0].id
  path_part   = "{id}"
}

resource "aws_api_gateway_resource" "localstack_transaction" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_posting_id[0].id
  path_part   = "transaction"
}

resource "aws_api_gateway_resource" "localstack_transaction_order" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_transaction[0].id
  path_part   = "{order}"
}

resource "aws_api_gateway_resource" "localstack_config" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_account_posting[0].id
  path_part   = "config"
}

resource "aws_api_gateway_resource" "localstack_config_type" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_config[0].id
  path_part   = "{type}"
}

resource "aws_api_gateway_resource" "localstack_config_type_order" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id
  parent_id   = aws_api_gateway_resource.localstack_config_type[0].id
  path_part   = "{order}"
}

resource "aws_api_gateway_method" "localstack_create_posting" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_account_posting[0].id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "localstack_create_posting" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_account_posting[0].id
  http_method             = aws_api_gateway_method.localstack_create_posting[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.main.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_search" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_search[0].id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "localstack_ops_search" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_search[0].id
  http_method             = aws_api_gateway_method.localstack_ops_search[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_search_get" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_search[0].id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "localstack_ops_search_get" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_search[0].id
  http_method             = aws_api_gateway_method.localstack_ops_search_get[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_retry" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_retry[0].id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "localstack_ops_retry" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_retry[0].id
  http_method             = aws_api_gateway_method.localstack_ops_retry[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_get_posting" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_posting_id[0].id
  http_method   = "GET"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.id" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_get_posting" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_posting_id[0].id
  http_method             = aws_api_gateway_method.localstack_ops_get_posting[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_list_legs" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_transaction[0].id
  http_method   = "GET"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.id" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_list_legs" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_transaction[0].id
  http_method             = aws_api_gateway_method.localstack_ops_list_legs[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_get_leg" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_transaction_order[0].id
  http_method   = "GET"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.id"    = true
    "method.request.path.order" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_get_leg" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_transaction_order[0].id
  http_method             = aws_api_gateway_method.localstack_ops_get_leg[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_patch_leg" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_transaction_order[0].id
  http_method   = "PATCH"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.id"    = true
    "method.request.path.order" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_patch_leg" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_transaction_order[0].id
  http_method             = aws_api_gateway_method.localstack_ops_patch_leg[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_list_config" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_config[0].id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "localstack_ops_list_config" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_config[0].id
  http_method             = aws_api_gateway_method.localstack_ops_list_config[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_get_config_by_type" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_config_type[0].id
  http_method   = "GET"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.type" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_get_config_by_type" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_config_type[0].id
  http_method             = aws_api_gateway_method.localstack_ops_get_config_by_type[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_create_config" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_config[0].id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "localstack_ops_create_config" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_config[0].id
  http_method             = aws_api_gateway_method.localstack_ops_create_config[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_update_config" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_config_type_order[0].id
  http_method   = "PUT"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.type"  = true
    "method.request.path.order" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_update_config" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_config_type_order[0].id
  http_method             = aws_api_gateway_method.localstack_ops_update_config[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_method" "localstack_ops_delete_config" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  resource_id   = aws_api_gateway_resource.localstack_config_type_order[0].id
  http_method   = "DELETE"
  authorization = "NONE"

  request_parameters = {
    "method.request.path.type"  = true
    "method.request.path.order" = true
  }
}

resource "aws_api_gateway_integration" "localstack_ops_delete_config" {
  count                   = var.use_localstack ? 1 : 0
  rest_api_id             = aws_api_gateway_rest_api.localstack[0].id
  resource_id             = aws_api_gateway_resource.localstack_config_type_order[0].id
  http_method             = aws_api_gateway_method.localstack_ops_delete_config[0].http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.ops.invoke_arn
}

resource "aws_api_gateway_deployment" "localstack" {
  count       = var.use_localstack ? 1 : 0
  rest_api_id = aws_api_gateway_rest_api.localstack[0].id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_integration.localstack_create_posting[0].id,
      aws_api_gateway_integration.localstack_ops_search[0].id,
      aws_api_gateway_integration.localstack_ops_search_get[0].id,
      aws_api_gateway_integration.localstack_ops_retry[0].id,
      aws_api_gateway_integration.localstack_ops_get_posting[0].id,
      aws_api_gateway_integration.localstack_ops_list_legs[0].id,
      aws_api_gateway_integration.localstack_ops_get_leg[0].id,
      aws_api_gateway_integration.localstack_ops_patch_leg[0].id,
      aws_api_gateway_integration.localstack_ops_list_config[0].id,
      aws_api_gateway_integration.localstack_ops_get_config_by_type[0].id,
      aws_api_gateway_integration.localstack_ops_create_config[0].id,
      aws_api_gateway_integration.localstack_ops_update_config[0].id,
      aws_api_gateway_integration.localstack_ops_delete_config[0].id
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "localstack" {
  count         = var.use_localstack ? 1 : 0
  rest_api_id   = aws_api_gateway_rest_api.localstack[0].id
  deployment_id = aws_api_gateway_deployment.localstack[0].id
  stage_name    = var.environment
}
