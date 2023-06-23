data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = "${path.module}/../../sierra_reader"
  output_path = "${path.module}/sierra_reader.zip"
}

locals {
  # Max runtime is 15 minutes, we want to give this as long as possible
  # in case it gets a lot of updates in a window.
  timeout_in_minutes = 15
}

module "lambda" {
  source      = "../../../infrastructure/modules/lambda"
  s3_bucket   = "wellcomecollection-platform-infra"
  s3_key      = "lambdas/sierra_adapter/sierra_reader.zip"
  module_name = "sierra_reader"

  description     = "Fetch records from Sierra and send them to SNS"
  name            = "${var.namespace}-sierra-reader-${var.resource_type}"
  alarm_topic_arn = var.lambda_error_alarm_arn

  environment_variables = {
    TOPIC_ARN     = module.output_topic.arn
    RESOURCE_TYPE = var.resource_type
    SIERRA_FIELDS = var.sierra_fields
    READER_BUCKET = var.reader_bucket

    TIMEOUT_IN_MINUTES = local.timeout_in_minutes
  }

  runtime = "python3.9"

  timeout = local.timeout_in_minutes * 60
}

resource "aws_lambda_permission" "allow_sns_trigger" {
  for_each = toset(var.windows_topic_arns)

  action        = "lambda:InvokeFunction"
  function_name = module.lambda.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = each.key
}

resource "aws_sns_topic_subscription" "input_to_lambda" {
  for_each = toset(var.windows_topic_arns)

  topic_arn = each.key
  protocol  = "lambda"
  endpoint  = module.lambda.arn
}
