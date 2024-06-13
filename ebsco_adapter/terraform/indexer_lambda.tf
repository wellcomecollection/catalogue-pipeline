module "indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-indexer"
  description = "Indexes EBSCO fields into the reporting cluster."
  runtime     = "python3.10"

  filename    = data.archive_file.empty_zip.output_path
  handler     = "main.lambda_handler"
  memory_size = 512
  timeout     = 60 // 1 minute

  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]

  environment = {
    variables = {
      ES_INDEX = "ebsco_fields"
    }
  }

  depends_on = [
    aws_s3_bucket.ebsco_adapter,
  ]
}

data "aws_iam_policy_document" "read_ebsco_adapter_bucket" {
  statement {
    actions = [
      "s3:GetObject",
    ]

    resources = [
      "${aws_s3_bucket.ebsco_adapter.arn}/*"
    ]
  }
}

data "aws_iam_policy_document" "allow_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/es_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/ebsco_indexer*"
    ]
  }
}

resource "aws_iam_role_policy" "read_secrets_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "indexer_lambda_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.read_ebsco_adapter_bucket.json
}

resource "aws_sns_topic_subscription" "indexer_lambda_subscription" {
  topic_arn = module.ebsco_adapter_output_topic.arn
  protocol  = "lambda"
  endpoint  = module.indexer_lambda.lambda.arn
}

resource "aws_lambda_permission" "allow_indexer_lambda_sns_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.indexer_lambda.lambda.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = module.ebsco_adapter_output_topic.arn
}
