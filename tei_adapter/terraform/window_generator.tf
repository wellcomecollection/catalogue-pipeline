module "tei_window_generator_lambda" {
  source = "./modules/scheduled_lambda"

  name        = "tei_window_generator"
  description = "Sends windows to the Tei adapter"

  s3_bucket         = local.infra_bucket
  s3_key            = "lambdas/tei_adapter/tei_window_generator.zip"
  schedule_interval = local.window_generator_interval

  env_vars = {
    TOPIC_ARN = aws_sns_topic.tei_windows_topic.arn
    WINDOW_LENGTH_MINUTES = 45
  }
}

data "aws_iam_policy_document" "publish_to_windows_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      aws_sns_topic.tei_windows_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "windows_policy" {
  role   = module.tei_window_generator_lambda.role_name
  policy = data.aws_iam_policy_document.publish_to_windows_topic.json
}
