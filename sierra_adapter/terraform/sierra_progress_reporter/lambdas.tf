module "lambda" {
  source = "../../../infrastructure/modules/lambda"

  name = "${var.namespace}-sierra_progress_reporter"

  module_name = "sierra_progress_reporter"

  s3_bucket = var.infra_bucket
  s3_key    = "lambdas/sierra_adapter/sierra_progress_reporter.zip"

  description     = "Run progress reports against the Sierra reader"
  alarm_topic_arn = var.lambda_error_alarm_arn
  timeout         = 900

  runtime = "python3.9"

  environment_variables = {
    BUCKET = var.s3_adapter_bucket_name
  }

  log_retention_in_days = 30
}

resource "random_id" "cloudwatch_trigger_name" {
  byte_length = 8
  prefix      = "AllowExecutionFromCloudWatch_${module.lambda.function_name}_"
}

resource "aws_lambda_permission" "allow_cloudwatch_trigger" {
  statement_id  = random_id.cloudwatch_trigger_name.id
  action        = "lambda:InvokeFunction"
  function_name = module.lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.rule.arn
}

resource "aws_cloudwatch_event_target" "event_trigger_custom" {
  rule  = aws_cloudwatch_event_rule.rule.id
  arn   = module.lambda.arn
  input = "{}"
}
