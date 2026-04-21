terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Uncomment and configure for remote state
  # backend "s3" {
  #   bucket = "your-terraform-state-bucket"
  #   key    = "account-posting/terraform.tfstate"
  #   region = "ap-southeast-1"
  # }
}

provider "aws" {
  region = var.aws_region

  dynamic "endpoints" {
    for_each = var.use_localstack ? [1] : []
    content {
      apigateway   = var.localstack_endpoint
      apigatewayv2 = var.localstack_endpoint
      cloudwatch   = var.localstack_endpoint
      cloudwatchlogs = var.localstack_endpoint
      dynamodb     = var.localstack_endpoint
      iam          = var.localstack_endpoint
      lambda       = var.localstack_endpoint
      sns          = var.localstack_endpoint
      sqs          = var.localstack_endpoint
      sts          = var.localstack_endpoint
    }
  }

  access_key = var.use_localstack ? "test" : null
  secret_key = var.use_localstack ? "test" : null

  skip_credentials_validation = var.use_localstack
  skip_metadata_api_check     = var.use_localstack
  skip_requesting_account_id  = var.use_localstack
  skip_region_validation      = var.use_localstack

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}
