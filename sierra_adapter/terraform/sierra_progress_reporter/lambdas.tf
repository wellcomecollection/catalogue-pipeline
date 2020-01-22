module "lambda" {
  source = "../modules/lambda"

  name = "sierra_progress_reporter"

  s3_bucket = var.infra_bucket
  s3_key    = "lambdas/sierra_adapter/sierra_progress_reporter.zip"

  description     = "Run progress reports against the Sierra reader"
  alarm_topic_arn = var.lambda_error_alarm_arn
  timeout         = 300

  environment_variables = {
    BUCKET        = var.s3_adapter_bucket_name
    SLACK_WEBHOOK = var.slack_access_token
  }

  log_retention_in_days = 30
}

module "trigger_sierra_window_generator_lambda" {
  source                  = "git::https://github.com/wellcometrust/terraform.git//lambda/trigger_cloudwatch?ref=v1.0.0"
  lambda_function_name    = module.lambda.function_name
  lambda_function_arn     = module.lambda.arn
  cloudwatch_trigger_arn  = aws_cloudwatch_event_rule.rule.arn
  cloudwatch_trigger_name = aws_cloudwatch_event_rule.rule.id

  # This exists to tell the module "yes, really do create this trigger".
  # It's a bit of a hack to fit the way the module is written: internally it's
  # computing "${1 - var.custom_input}" to decide if you want a custom trigger.
  custom_input = 1
}
