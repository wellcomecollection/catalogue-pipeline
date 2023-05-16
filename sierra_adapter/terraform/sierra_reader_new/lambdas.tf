data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = "${path.module}/../../sierra_reader_new"
  output_path = "${path.module}/sierra_reader_new.zip"
}

module "lambda" {
  source      = "../../../infrastructure/modules/lambda"
  s3_bucket   = var.sierra_reader_zip["bucket"]
  s3_key      = var.sierra_reader_zip["key"]
  module_name = "sierra_reader"

  description     = "Fetch records from Sierra and send them to SNS"
  name            = "${var.namespace}-sierra-reader-${var.resource_type}"
  alarm_topic_arn = var.lambda_error_alarm_arn

  runtime = "python3.9"

  # max timeout for a Lambda is 15 minutes
  timeout = 15 * 60
}

/*resource "aws_lambda_permission" "allow_sns_trigger" {
  for_each = toset(var.windows_topic_arns)

  action        = "lambda:InvokeFunction"
  function_name = module.lambda.arn
  principal     = "sns.amazonaws.com"
  source_arn    = each.key
  depends_on    = [aws_sns_topic_subscription.input_to_lambda]
}

resource "aws_sns_topic_subscription" "input_to_lambda" {
  for_each = toset(var.windows_topic_arns)

  topic_arn = aws_sns_topic.input.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.lambda_function.arn
}
*/
