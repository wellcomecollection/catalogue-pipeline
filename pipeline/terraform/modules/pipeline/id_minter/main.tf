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

  environment_variables = var.id_minter_env_vars
  secret_env_vars       = var.id_minter_secret_env_vars

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
    RDS_MAX_CONNECTIONS = number
    LOG_LEVEL           = optional(string, "INFO")
  })
}

variable "id_minter_secret_env_vars" {
  type = object({
    RDS_PRIMARY_HOST = string
    RDS_REPLICA_HOST = string
    RDS_PORT         = string
    RDS_USERNAME     = string
    RDS_PASSWORD     = string
  })
}