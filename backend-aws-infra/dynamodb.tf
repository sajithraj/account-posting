# ─────────────────────────────────────────────
# Table 1: account_posting
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "account_posting" {
  name         = "${local.name_prefix}-posting"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "postingId"

  attribute {
    name = "postingId"
    type = "N"
  }

  attribute {
    name = "endToEndReferenceId"
    type = "S"
  }

  attribute {
    name = "status"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  attribute {
    name = "sourceName"
    type = "S"
  }

  # GSI1: idempotency check on create + exact search
  global_secondary_index {
    name            = "gsi-endToEndReferenceId"
    hash_key        = "endToEndReferenceId"
    projection_type = "ALL"
  }

  # GSI2: search by status + date range, retry eligibility scan
  global_secondary_index {
    name            = "gsi-status-createdAt"
    hash_key        = "status"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  # GSI3: search by source + date range
  global_secondary_index {
    name            = "gsi-sourceName-createdAt"
    hash_key        = "sourceName"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  # TTL: auto-delete after 60 days
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "${local.name_prefix}-posting"
  }
}

# ─────────────────────────────────────────────
# Table 2: account_posting_leg
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "account_posting_leg" {
  name         = "${local.name_prefix}-leg"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "postingId"
  range_key    = "transactionOrder"

  attribute {
    name = "postingId"
    type = "N"
  }

  attribute {
    name = "transactionOrder"
    type = "N"
  }

  # TTL: auto-delete after 60 days
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "${local.name_prefix}-leg"
  }
}

# ─────────────────────────────────────────────
# Table 3: account_posting_config
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "account_posting_config" {
  name         = "${local.name_prefix}-config"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "requestType"
  range_key    = "orderSeq"

  attribute {
    name = "requestType"
    type = "S"
  }

  attribute {
    name = "orderSeq"
    type = "N"
  }

  tags = {
    Name = "${local.name_prefix}-config"
  }
}
