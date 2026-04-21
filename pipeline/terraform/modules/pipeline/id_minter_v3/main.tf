module "id_minter_v3_output_topic" {
  source = "../../topic"

  name       = "catalogue-${var.pipeline_date}_id_minter_v3_output"
  role_names = [module.id_minter_v3_lambda.lambda_role_name]
}

module "id_minter_v3_lambda" {
  source = "../../pipeline_lambda"

  service_name = "id-minter"
  description  = "id-minter (V3) service for the WCSTP test catalogue pipeline"

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["id_minter.steps.id_minter.lambda_handler"]
  }

  memory_size = 2048
  timeout     = 900

  environment_variables = merge(
    { for k, v in var.id_minter_v3_env_vars : k => tostring(v) if v != null },
    {
      PIPELINE_DATE            = var.pipeline_date
      DOWNSTREAM_SNS_TOPIC_ARN = module.id_minter_v3_output_topic.arn
    }
  )
  secret_env_vars = var.id_minter_v3_secret_env_vars

  vpc_config = var.id_minter_v3_vpc_config
}

resource "aws_iam_role_policy" "id_minter_v3_lambda_s3_write" {
  count = var.id_minter_v3_env_vars.S3_BUCKET != null ? 1 : 0

  name   = "id-minter-v3-s3-write"
  role   = module.id_minter_v3_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.id_minter_v3_lambda_s3_write[0].json
}

data "aws_iam_policy_document" "id_minter_v3_lambda_s3_write" {
  count = var.id_minter_v3_env_vars.S3_BUCKET != null ? 1 : 0

  statement {
    actions = ["s3:PutObject"]
    resources = [
      "arn:aws:s3:::${var.id_minter_v3_env_vars.S3_BUCKET}/${var.id_minter_v3_env_vars.S3_PREFIX}/id_minter/*",
    ]
  }
}

resource "aws_iam_role_policy" "id_minter_v3_cloudwatch_write_policy" {
  role   = module.id_minter_v3_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.id_minter_v3_cloudwatch_write.json
}

data "aws_iam_policy_document" "id_minter_v3_cloudwatch_write" {
  statement {
    actions = [
      "cloudwatch:PutMetricData"
    ]

    resources = [
      "*"
    ]
  }
}
