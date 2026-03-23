module "id_minter_output_topic" {
  source = "../../topic"

  name       = "catalogue-${var.pipeline_date}_id_minter_output"
  role_names = [module.id_minter_lambda.lambda_role_name]
}

module "id_minter_lambda" {
  source = "../../pipeline_lambda"

  service_name = "id-minter"
  description  = "id-minter (V2) service for the catalogue pipeline"

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["id_minter.steps.id_minter.lambda_handler"]
  }

  memory_size = 2048
  timeout     = 300

  environment_variables = merge(
    { for k, v in var.id_minter_env_vars : k => tostring(v) if v != null },
    {
      PIPELINE_DATE            = var.pipeline_date
      DOWNSTREAM_SNS_TOPIC_ARN = module.id_minter_output_topic.arn
    }
  )
  secret_env_vars = var.id_minter_secret_env_vars

  vpc_config = var.id_minter_vpc_config
}

module "id_generator_lambda" {
  source = "../../pipeline_lambda"

  service_name = "id-generator"
  description  = "Lambda to pre-generate canonical IDs"

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["id_minter.steps.id_generator.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 60 * 5 # 5 Minutes

  environment_variables = {
    PIPELINE_DATE = var.pipeline_date
  }

  secret_env_vars = var.id_minter_secret_env_vars

  vpc_config = var.id_minter_vpc_config
}

resource "aws_iam_role_policy" "id_minter_lambda_s3_write" {
  count = var.id_minter_env_vars.S3_BUCKET != null ? 1 : 0

  name   = "id-minter-s3-write"
  role   = module.id_minter_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.id_minter_s3_write[0].json
}

data "aws_iam_policy_document" "id_minter_s3_write" {
  count = var.id_minter_env_vars.S3_BUCKET != null ? 1 : 0

  statement {
    actions = ["s3:PutObject"]
    resources = [
      "arn:aws:s3:::${var.id_minter_env_vars.S3_BUCKET}/${var.id_minter_env_vars.S3_PREFIX}/id_minter/*",
    ]
  }
}

resource "aws_iam_role_policy" "id_minter_cloudwatch_write_policy" {
  role   = module.id_minter_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.id_minter_cloudwatch_write.json
}

data "aws_iam_policy_document" "id_minter_cloudwatch_write" {
  statement {
    actions = [
      "cloudwatch:PutMetricData"
    ]

    resources = [
      "*"
    ]
  }
}
