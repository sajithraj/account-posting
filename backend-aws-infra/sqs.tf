resource "aws_sqs_queue" "processing_queue" {
  name = "${local.name_prefix}-processing-queue"

  # How long a message is hidden after being picked up
  visibility_timeout_seconds = var.sqs_visibility_timeout_seconds

  # Message retention: 4 days (default)
  message_retention_seconds = 345600

  # No DLQ — posting stays PNDG in DynamoDB on failure;
  # support team retries manually via dashboard
  # Max receive count = 1 enforced via Lambda event source mapping
  redrive_policy = null

  tags = {
    Name = "${local.name_prefix}-processing-queue"
  }
}
