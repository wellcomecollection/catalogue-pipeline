resource "aws_lambda_function" "window_generator_lambda" {

  function_name = "calm_window_generator"
  description = "Sends windows to the Calm adapter"

  role = aws_iam_role.window_generator_role.arn

  handler = "lambda.main"
  runtime = "python3.6"

  s3_bucket = data.aws_s3_bucket_object.package.bucket
  s3_key = data.aws_s3_bucket_object.package.key
  s3_object_version = data.aws_s3_bucket_object.package.version_id

  environment {
    variables = {
      "TOPIC_ARN" = aws_sns_topic.calm_windows_topic.arn
    }
  }
}

resource "aws_iam_role" "window_generator_role" {
  name               = "lambda_calm_window_generator_iam_role"
  assume_role_policy = data.aws_iam_policy_document.assume_lambda_role.json
}

data "aws_iam_policy_document" "assume_lambda_role" {
  statement {
    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

data "aws_s3_bucket_object" "package" {
  bucket = local.infra_bucket
  key = "lambdas/calm_adapter/calm_window_generator.zip"
}

resource "aws_cloudwatch_event_rule" "window_generator_rule" {
  name = "calm_window_generator_rule"
  description = "Starts the calm_window_generator lambda"
  schedule_expression = "rate(${local.window_generator_interval})"
}

resource "aws_cloudwatch_event_target" "window_generator_event_target" {
  rule      = aws_cloudwatch_event_rule.window_generator_rule.name
  target_id = "calm_window_generator_target"
  arn       = aws_lambda_function.window_generator_lambda.arn
}

resource "aws_lambda_permission" "allow_cloudwatch_to_call_window_generator" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.window_generator_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.window_generator_rule.arn
}
