resource "aws_sns_topic" "support_alert" {
  name = "${local.name_prefix}-support-alert"

  tags = {
    Name = "${local.name_prefix}-support-alert"
  }
}

resource "aws_sns_topic_subscription" "support_email" {
  count = var.use_localstack ? 0 : 1

  topic_arn = aws_sns_topic.support_alert.arn
  protocol  = "email"
  endpoint  = var.support_email
}
