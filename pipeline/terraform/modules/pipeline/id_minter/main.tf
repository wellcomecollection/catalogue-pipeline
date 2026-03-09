module "id_minter_output_topic" {
  source = "../../../topic"

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

  memory_size = 256
  timeout     = 60

  environment_variables = merge(
    { for k, v in var.id_minter_env_vars : k => tostring(v) if v != null },
    {
      PIPELINE_DATE              = var.pipeline_date
      DOWNSTREAM_SNS_TOPIC_ARN   = module.id_minter_output_topic.arn
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

variable "id_minter_env_vars" {
  type = object({
    RDS_MAX_CONNECTIONS         = number
    LOG_LEVEL                   = optional(string, "INFO")
    ES_SOURCE_INDEX_PREFIX      = optional(string, "works-source")
    ES_TARGET_INDEX_PREFIX      = optional(string, "works-identified")
    ES_SOURCE_INDEX_DATE_SUFFIX = optional(string)
    ES_TARGET_INDEX_DATE_SUFFIX = optional(string)
    APPLY_MIGRATIONS            = optional(string, "false")
    S3_BUCKET                   = optional(string)
    S3_PREFIX                   = optional(string, "dev")
  })
}

variable "id_minter_secret_env_vars" {
  type = map(string)
}