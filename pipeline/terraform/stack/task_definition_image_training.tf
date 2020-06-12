locals {
  name   = "${local.namespace_hyphen}_image_training"
  cpu    = 4096
  memory = 8192

  env_vars = {
    MODEL_DATA_BUCKET = var.inferrer_model_data_bucket_name,
    ES_INDEX          = var.es_images_index
  }

  secret_env_vars = {
    ES_HOST     = "catalogue/image_training/es_host"
    ES_PORT     = "catalogue/image_training/es_port"
    ES_USERNAME = "catalogue/image_training/es_username"
    ES_PASSWORD = "catalogue/image_training/es_password"
    ES_PROTOCOL = "catalogue/image_training/es_protocol"
  }
}

module "app_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v2.4.1"

  name  = local.name
  image = local.feature_training_image

  cpu    = local.cpu
  memory = local.memory

  environment = local.env_vars
  secrets     = local.secret_env_vars

  log_configuration = module.log_router_container.container_log_configuration
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v2.4.1"
  namespace = local.name
}

module "task_definition_image_training" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v2.4.1"

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
  policy = data.aws_iam_policy_document.allow_model_write
}

data "aws_iam_policy_document" "allow_model_write" {
  statement {
    // Actions  = write s3, ssm
    // Resources = model bucket, ssm
  }
}
