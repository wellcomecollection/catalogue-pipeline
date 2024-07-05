module "cloudwatch_alarm_to_slack_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "cloudwatch-alarm-to-slack"
  description = "Sends CloudWatch alarms to a Slack channel."
  runtime     = "python3.10"

  filename    = data.archive_file.cloudwatch_alarm_to_slack.output_path
  handler     = "cloudwatch_alarm_to_slack.lambda_handler"
  memory_size = 512
  timeout     = 60 // 1 minute

  source_code_hash = data.archive_file.cloudwatch_alarm_to_slack.output_base64sha256

  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]

  environment = {
    variables = {
      HOOK_URL      = aws_ssm_parameter.cloudwatch_alarm_to_slack_hook_url.value
      SLACK_CHANNEL = aws_ssm_parameter.cloudwatch_alarm_to_slack_channel.value
    }
  }
}

resource "aws_ssm_parameter" "cloudwatch_alarm_to_slack_hook_url" {
  name        = "/catalogue_pipeline/ebsco_adapter/cloudwatch_alarm_to_slack_hook_url"
  description = "The URL of the Slack webhook to send messages to"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "cloudwatch_alarm_to_slack_channel" {
  name        = "/catalogue_pipeline/ebsco_adapter/cloudwatch_alarm_to_slack_channel"
  description = "The Slack channel to send messages to"
  type        = "String"
  value       = "wc-platform-alerts"
}

module "cloudwatch_alarm_to_slack_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "cloudwatch_alarm_to_slack"
}

resource "aws_sns_topic_subscription" "cloudwatch_alarm_to_slack_subscription" {
  topic_arn = module.cloudwatch_alarm_to_slack_topic.arn
  protocol  = "lambda"
  endpoint  = module.cloudwatch_alarm_to_slack_lambda.lambda.arn
}

resource "aws_lambda_permission" "allow_execution_from_sns" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = module.cloudwatch_alarm_to_slack_lambda.lambda.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = module.cloudwatch_alarm_to_slack_topic.arn
}
