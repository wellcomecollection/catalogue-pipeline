module "snapshot_reporter" {
  source = "../../modules/lambda"

  name = "snapshot_reporter-${var.deployment_service_env}"

  s3_bucket = var.lambda_upload_bucket
  s3_key    = "lambdas/snapshots/snapshot_reporter.zip"

  description     = "Reports daily snapshot results to Slack"
  alarm_topic_arn = var.lambda_error_alarm_arn
  timeout         = 60

  env_vars = {
    ELASTIC_SECRET_ID = local.elastic_secret_id
    SLACK_SECRET_ID   = local.slack_secret_id
    ELASTIC_INDEX     = "snapshots"
  }

  log_retention_in_days = 30

  handler = "snapshot_reporter"
}
