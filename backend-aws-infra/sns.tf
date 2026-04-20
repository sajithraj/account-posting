resource "aws_sns_topic" "support_alert" {
  name = "${local.name_prefix}-support-alert"

  tags = {
    Name = "${local.name_prefix}-support-alert"
  }
}

resource "aws_sns_topic_subscription" "support_email" {
  topic_arn = aws_sns_topic.support_alert.arn
  protocol  = "email"
  endpoint  = var.support_email
}
