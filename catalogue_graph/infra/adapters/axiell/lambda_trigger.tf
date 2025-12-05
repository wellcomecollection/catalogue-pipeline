module "trigger_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "axiell-adapter-trigger"
  description  = "Lambda function to trigger Axiell adapter ingestion"
  package_type = "Image"
  image_uri    = "${var.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["adapters.axiell.steps.trigger.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 300

  environment = {
    variables = {
      S3_BUCKET         = data.aws_s3_bucket.axiell_adapter.id
      S3_PREFIX         = "prod"
      CHATBOT_TOPIC_ARN = local.chatbot_topic_arn
    }
  }
}

resource "aws_iam_role_policy" "trigger_lambda_ssm_read" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ssm_read.json
}

resource "aws_iam_role_policy" "trigger_lambda_iceberg_write" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.iceberg_write.json
}

# IAM policies for Axiell adapter S3 bucket
resource "aws_iam_role_policy" "trigger_lambda_s3_read" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_read.json
}

resource "aws_iam_role_policy" "trigger_lambda_s3_write" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_write.json
}

resource "aws_iam_role_policy" "trigger_lambda_chatbot_topic_publish" {
  role   = module.trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.chatbot_topic_publish.json
}
