module "app_container_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v4.3.1"
  name   = var.task_name
  image  = var.image

  environment = var.environment
  secrets     = var.secret_env_vars

  log_configuration = module.log_router_container.container_log_configuration
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v4.3.1"
  namespace = var.task_name

  # aws-for-fluent-bit 3.5.5 (wellcomecollection/platform-infrastructure#624)
  container_tag            = "0b9948b83bfb68e4481ed988f15ede80239e4879"
  use_privatelink_endpoint = true
}

module "log_router_container_secrets_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.1"
  secrets   = module.log_router_container.shared_secrets_logging
  role_name = module.task_definition.task_execution_role_name
}

module "app_secrets_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.1"
  secrets   = var.secret_env_vars
  role_name = module.task_definition.task_execution_role_name
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v4.3.1"

  cpu    = var.cpu
  memory = var.memory

  ephemeral_storage_size = var.ephemeral_storage_size

  container_definitions = [
    module.log_router_container.container_definition,
    module.app_container_definition.container_definition
  ]

  launch_types = ["FARGATE"]
  task_name    = var.task_name
}
