module "indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name    = "ebsco-adapter-indexer"
  runtime = "python3.10"

  filename    = data.archive_file.empty_zip.output_path
  handler     = "main.lambda_handler"
  memory_size = 512
  timeout     = 10 * 60 // 10 minutes

  environment = {
    variables = {
      S3_BUCKET = aws_s3_bucket.ebsco_adapter.bucket
      S3_PREFIX = "prod"
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
      "${aws_s3_bucket.ebsco_adapter.arn}/xml/*"
    ]
  }
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
