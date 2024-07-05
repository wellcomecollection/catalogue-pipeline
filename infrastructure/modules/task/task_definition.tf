module "app_container_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.13.1"
  name   = "ebsco-adapter-ftp"
  image  = var.image

  environment = var.environment

  log_configuration = module.log_router_container.container_log_configuration
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v3.13.1"
  namespace = var.task_name

  use_privatelink_endpoint = true
}

module "log_router_container_secrets_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.13.1"
  secrets   = module.log_router_container.shared_secrets_logging
  role_name = module.task_definition.task_execution_role_name
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v3.13.1"

  cpu    = var.cpu
  memory = var.memory

  container_definitions = [
    module.log_router_container.container_definition,
    module.app_container_definition.container_definition
  ]

  launch_types = ["FARGATE"]
  task_name    = var.task_name
}
