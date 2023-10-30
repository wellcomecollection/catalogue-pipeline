
module "tei_updater_lambda" {
  source = "../../../infrastructure/modules/lambda"

  name = "tei_updater"

  s3_bucket   = var.infra_bucket
  s3_key      = "lambdas/tei_adapter/tei_updater.zip"
  module_name = "tei_updater"

  description     = "Update the tei tree"
  alarm_topic_arn = var.lambda_error_alarm_arn
  timeout         = 60

  environment_variables = {
    TOPIC_ARN           = module.tei_adapter_topic.arn
    BUCKET_NAME         = aws_s3_bucket.tei_adapter.id
    TREE_FILE_KEY       = var.tei_tree_key
    GITHUB_API_URL      = var.github_url
    GITHUB_TOKEN_SECRET = var.github_token_secret
  }
  memory_size           = 1024
  log_retention_in_days = 30
}

resource "random_id" "cloudwatch_trigger_name" {
  byte_length = 8
  prefix      = "AllowExecutionFromCloudWatch_${module.tei_updater_lambda.function_name}_"
}

resource "aws_lambda_permission" "allow_cloudwatch_trigger" {
  statement_id  = random_id.cloudwatch_trigger_name.id
  action        = "lambda:InvokeFunction"
  function_name = module.tei_updater_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.tei_updater_rule.arn
}

resource "aws_cloudwatch_event_target" "event_trigger_custom" {
  rule  = aws_cloudwatch_event_rule.tei_updater_rule.id
  arn   = module.tei_updater_lambda.arn
  input = "{}"
}
