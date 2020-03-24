module "lambda_snapshot_slack_alarms" {
  source = "../modules/lambda"

  s3_bucket = local.infra_bucket
  s3_key    = "lambdas/data_api/snapshot_slack_alarms.zip"

  name        = "snapshot_slack_alarms"
  description = "Post a notification to Slack when a snapshot fails"
  timeout     = 10

  env_vars = {
    CRITICAL_SLACK_WEBHOOK = var.critical_slack_webhook
  }

  alarm_topic_arn = local.lambda_error_alarm_arn

  log_retention_in_days = 30
}

data "aws_iam_policy_document" "read_from_snapshots_bucket" {
  statement {
    actions = [
      "s3:Head*",
      "s3:List*",
    ]

    resources = [
      "${aws_s3_bucket.public_data.arn}/",
      "${aws_s3_bucket.public_data.arn}/*",
    ]
  }
}

resource "aws_iam_role_policy" "snapshot_alarms_read_from_bucket" {
  role   = module.lambda_snapshot_slack_alarms.role_name
  policy = data.aws_iam_policy_document.read_from_snapshots_bucket.json
}

resource "aws_sns_topic_subscription" "lambda_snapshot_slack_alarms" {
  topic_arn = module.snapshot_alarm_topic.arn
  protocol  = "lambda"
  endpoint  = module.lambda_snapshot_slack_alarms.arn
}

resource "aws_lambda_permission" "lambda_snapshot_slack_alarms" {
  action        = "lambda:InvokeFunction"
  function_name = module.lambda_snapshot_slack_alarms.arn
  principal     = "sns.amazonaws.com"
  source_arn    = module.snapshot_alarm_topic.arn
  depends_on    = [aws_sns_topic_subscription.lambda_snapshot_slack_alarms]
}
