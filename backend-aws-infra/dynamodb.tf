# ─────────────────────────────────────────────
# Table 1: account_posting
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "account_posting" {
  name         = "account_posting"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "postingId"

  attribute {
    name = "postingId"
    type = "S"
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
    name = "updatedAt"
    type = "S"
  }

  attribute {
    name = "sourceName"
    type = "S"
  }

  attribute {
    name = "sourceReferenceId"
    type = "S"
  }

  attribute {
    name = "requestType"
    type = "S"
  }

  attribute {
    name = "entityType"
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

  # GSI4: search by status sorted by last update
  global_secondary_index {
    name            = "gsi-status-updatedAt"
    hash_key        = "status"
    range_key       = "updatedAt"
    projection_type = "ALL"
  }

  # GSI5: search by source sorted by last update
  global_secondary_index {
    name            = "gsi-sourceName-updatedAt"
    hash_key        = "sourceName"
    range_key       = "updatedAt"
    projection_type = "ALL"
  }

  # GSI6: search by request type sorted by last update
  global_secondary_index {
    name            = "gsi-requestType-updatedAt"
    hash_key        = "requestType"
    range_key       = "updatedAt"
    projection_type = "ALL"
  }

  # GSI7: exact lookup by sourceReferenceId (unique, no date sort needed)
  global_secondary_index {
    name            = "gsi-sourceReferenceId"
    hash_key        = "sourceReferenceId"
    projection_type = "ALL"
  }

  # GSI8: search by entity type sorted by last update (for pure date range queries)
  global_secondary_index {
    name            = "gsi-entityType-updatedAt"
    hash_key        = "entityType"
    range_key       = "updatedAt"
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
    Name = "account_posting"
  }
}

# ─────────────────────────────────────────────
# Table 2: account_posting_transaction
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "account_posting_leg" {
  name         = "account_posting_transaction"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "postingId"
  range_key    = "transactionOrder"

  attribute {
    name = "postingId"
    type = "S"
  }

  attribute {
    name = "transactionOrder"
    type = "N"
  }

  attribute {
    name = "transactionId"
    type = "S"
  }

  # GSI: lookup by UUID transactionId
  global_secondary_index {
    name            = "gsi-transactionId"
    hash_key        = "transactionId"
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
    Name = "account_posting_transaction"
  }
}

# ─────────────────────────────────────────────
# Table 3: account_posting_config
# ─────────────────────────────────────────────
resource "aws_dynamodb_table" "account_posting_config" {
  name         = "account_posting_config"
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

  attribute {
    name = "configId"
    type = "S"
  }

  # GSI: lookup by UUID configId for update/delete by ID
  global_secondary_index {
    name            = "gsi-configId"
    hash_key        = "configId"
    projection_type = "ALL"
  }

  tags = {
    Name = "account_posting_config"
  }
}
