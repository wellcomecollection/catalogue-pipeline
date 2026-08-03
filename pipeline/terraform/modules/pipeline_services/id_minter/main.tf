locals {
  underscore_namespace = var.namespace != "" ? "_${var.namespace}" : ""
  dash_namespace       = var.namespace != "" ? "-${var.namespace}" : ""

  pipeline_step = "id_minter${local.underscore_namespace}"
}

module "id_minter_output_topic" {
  source = "../../topic"

  name       = "catalogue-${var.pipeline_date}_${local.pipeline_step}_output"
  role_names = [module.id_minter_lambda.lambda_role_name]
}

module "id_minter_lambda" {
  source = "../../pipeline_lambda"

  service_name = "id-minter${local.dash_namespace}"
  description  = "id-minter${local.dash_namespace} (V2) service for the catalogue pipeline"

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["id_minter.steps.id_minter.lambda_handler"]
  }

  memory_size = var.memory_size
  timeout     = 900

  environment_variables = merge(
    { for k, v in var.env_vars : k => tostring(v) if v != null },
    {
      PIPELINE_DATE            = var.pipeline_date
      PIPELINE_STEP            = local.pipeline_step // used in CloudWatch metric dimensions
      DOWNSTREAM_SNS_TOPIC_ARN = module.id_minter_output_topic.arn
      RDS_SECRET_NAME          = var.rds_secret_name
    }
  )
  secret_env_vars = var.secret_env_vars
  vpc_config      = var.vpc_config
}

module "id_generator_lambda" {
  source = "../../pipeline_lambda"
  count  = var.include_id_generator ? 1 : 0

  service_name = "id-generator${local.dash_namespace}"
  description  = "Lambda${local.dash_namespace} to pre-generate canonical IDs"

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["id_minter.steps.id_generator.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 60 * 5 # 5 Minutes

  environment_variables = {
    PIPELINE_DATE   = var.pipeline_date
    RDS_SECRET_NAME = var.rds_secret_name
  }

  secret_env_vars = var.secret_env_vars

  vpc_config = var.vpc_config
}

# Granted explicitly rather than relying on the caller happening to list the
# same secret in secret_env_vars.
resource "aws_iam_role_policy" "id_minter_rds_secret_read" {
  name   = "id-minter${local.dash_namespace}-rds-secret-read"
  role   = module.id_minter_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.rds_secret_read.json
}

resource "aws_iam_role_policy" "id_generator_rds_secret_read" {
  count = var.include_id_generator ? 1 : 0

  name   = "id-generator${local.dash_namespace}-rds-secret-read"
  role   = module.id_generator_lambda[0].lambda_role_name
  policy = data.aws_iam_policy_document.rds_secret_read.json
}

data "aws_iam_policy_document" "rds_secret_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:secret:${var.rds_secret_name}-*",
    ]
  }
}

resource "aws_iam_role_policy" "id_minter_lambda_s3_write" {
  count = var.env_vars.S3_BUCKET != null ? 1 : 0

  name   = "id-minter${local.dash_namespace}-s3-write"
  role   = module.id_minter_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.id_minter_s3_write[0].json
}

data "aws_iam_policy_document" "id_minter_s3_write" {
  count = var.env_vars.S3_BUCKET != null ? 1 : 0

  statement {
    actions = ["s3:PutObject"]
    resources = [
      "arn:aws:s3:::${var.env_vars.S3_BUCKET}/pipeline-${var.pipeline_date}/${local.pipeline_step}/${var.env_vars.S3_PREFIX}/*",
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

# Rather than failing the whole state machine execution when the id_minter Lambda reports failures
# we use this alarm triggered by an existing CloudWatch metric
# This will give us visibility of failures in the id_minter step; we can reassess later if we want to add an automated retry mechanism
resource "aws_cloudwatch_metric_alarm" "id_minter_failures" {
  alarm_name          = "id-minter${local.dash_namespace}-failures-${var.pipeline_date}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "failure_count"
  namespace           = "catalogue_graph_pipeline"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "ID minter${local.dash_namespace} Lambda reported minting failures"

  dimensions = {
    pipeline_date = var.pipeline_date
    pipeline_step = local.pipeline_step
  }

  alarm_actions = [var.alarm_topic_arn]
}
