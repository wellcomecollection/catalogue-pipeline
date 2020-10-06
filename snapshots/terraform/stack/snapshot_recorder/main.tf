locals {
  secret_id = "catalogue/snapshots/write_user"
}

module "snapshot_recorder" {
  source = "../../modules/lambda"

  name = "snapshot_recorder-${var.deployment_service_env}"

  s3_bucket = var.lambda_upload_bucket
  s3_key    = "lambdas/snapshots/snapshot_recorder.zip"

  description     = "Record completed snapshot jobs to the reporting cluster"
  alarm_topic_arn = var.lambda_error_alarm_arn
  timeout         = 60

  env_vars = {
    SECRET_ID = local.secret_id
  }

  log_retention_in_days = 30

  handler = "snapshot_recorder"
}

resource "aws_lambda_permission" "with_sns" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = module.snapshot_recorder.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = var.snapshot_generator_output_topic_arn
}

resource "aws_sns_topic_subscription" "lambda" {
  topic_arn = var.snapshot_generator_output_topic_arn
  protocol  = "lambda"
  endpoint  = module.snapshot_recorder.arn
}

data "aws_secretsmanager_secret_version" "es_credentials" {
  secret_id = local.secret_id
}

data "aws_iam_policy_document" "read_es_secrets" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
    ]

    resources = [
      data.aws_secretsmanager_secret_version.es_credentials.arn,
    ]
  }
}

resource "aws_iam_role_policy" "allow_recorder_to_read_es_secrets" {
  role   = module.snapshot_recorder.role_name
  policy = data.aws_iam_policy_document.read_es_secrets.json
}
