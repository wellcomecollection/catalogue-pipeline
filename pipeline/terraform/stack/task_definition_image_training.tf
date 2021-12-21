locals {
  name   = "${local.namespace}_image_training"
  cpu    = 4096
  memory = 8192

  env_vars = {
    MODEL_DATA_BUCKET = var.inferrer_model_data_bucket_name,
    MODEL_SSM_PATH    = data.aws_ssm_parameter.latest_lsh_model_key.name
    ES_INDEX          = local.es_images_index
  }
  lsh_model_key = var.release_label == "prod" ? "prod" : "stage"

  secret_env_vars = {
    ES_HOST     = "catalogue/image_training/es_host"
    ES_PORT     = "catalogue/image_training/es_port"
    ES_USERNAME = "catalogue/image_training/es_username"
    ES_PASSWORD = "catalogue/image_training/es_password"
    ES_PROTOCOL = "catalogue/image_training/es_protocol"
  }
}

data "aws_ssm_parameter" "inferrer_lsh_model_key" {
  name = "/catalogue_pipeline/config/models/${local.lsh_model_key}/lsh_model"
}

data "aws_ssm_parameter" "latest_lsh_model_key" {
  name = "/catalogue_pipeline/config/models/latest/lsh_model"
}

module "app_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.5.2"

  name  = local.name
  image = local.feature_training_image

  cpu    = local.cpu
  memory = local.memory

  environment = local.env_vars
  secrets     = local.secret_env_vars

  log_configuration = module.log_router_container.container_log_configuration
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v3.5.2"
  namespace = local.name

  use_privatelink_endpoint = true
}

module "task_definition_image_training" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v3.5.2"

  cpu    = local.cpu
  memory = local.memory

  launch_types = ["FARGATE"]
  task_name    = local.name

  container_definitions = [
    module.log_router_container.container_definition,
    module.app_container.container_definition
  ]
}

resource "aws_iam_role_policy" "write_model_artifact" {
  role   = module.task_definition_image_training.task_role_name
  policy = data.aws_iam_policy_document.allow_model_write.json
}

data "aws_iam_policy_document" "allow_model_write" {
  statement {
    actions = [
      "s3:PutObject",
      "s3:ListBucket",
      "ssm:PutParameter"
    ]

    resources = [
      "arn:aws:s3:::${var.inferrer_model_data_bucket_name}",
      "arn:aws:s3:::${var.inferrer_model_data_bucket_name}/*",
      data.aws_ssm_parameter.latest_lsh_model_key.arn
    ]
  }
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.5.2"
  secrets   = local.secret_env_vars
  role_name = module.task_definition_image_training.task_execution_role_name
}

module "log_router_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.5.2"
  secrets   = module.log_router_container.shared_secrets_logging
  role_name = module.task_definition_image_training.task_execution_role_name
}
