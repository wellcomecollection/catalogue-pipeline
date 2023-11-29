module "lambda" {
  source = "../../../infrastructure/modules/lambda"

  name = "update_embargoed_holdings"

  s3_bucket   = var.infra_bucket
  s3_key      = "lambdas/sierra_adapter/update_embargoed_holdings.zip"
  module_name = "update_embargoed_holdings"

  description     = "Trigger an update of holdings whose electronic access is under embargo"
  alarm_topic_arn = var.lambda_error_alarm_arn

  # This has to send ~180 notifications to SNS, and it runs once a day --
  # we should never be worrying about the timeout.
  timeout = 5 * 60

  environment_variables = {
    HOLDINGS_READER_TOPIC_ARN = var.topic_arn
  }

  log_retention_in_days = 7
}

resource "aws_lambda_permission" "allow_cloudwatch_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.daily_rule.arn
}

resource "aws_cloudwatch_event_target" "event_trigger_custom" {
  rule  = aws_cloudwatch_event_rule.daily_rule.id
  arn   = module.lambda.arn
  input = "{}"
}
