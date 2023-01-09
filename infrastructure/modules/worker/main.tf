module "scaling_service" {
  source = "../scaling_service"

  name = var.name

  shared_logging_secrets = var.shared_logging_secrets

  service_name                 = var.service_name
  cluster_arn                  = var.cluster_arn
  cluster_name = var.cluster_name
  subnets                      = var.subnets
  namespace_id                 = var.namespace_id
  launch_type                  = var.launch_type
  desired_task_count           = var.desired_task_count
  use_fargate_spot             = var.use_fargate_spot
  capacity_provider_strategies = var.capacity_provider_strategies
  ordered_placement_strategies = var.ordered_placement_strategies
  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_sg_id
  security_group_ids = var.security_group_ids

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity


  cpu = var.cpu
  memory = var.memory

  container_definitions = [
    module.app_container.container_definition,
  ]
}

moved {
  from = module.log_router_container
  to   = module.scaling_service.module.log_router_container
}
moved {
  from = module.log_router_permissions
  to   = module.scaling_service.module.log_router_permissions
}

module "app_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.12.2"

  name  = var.name
  image = var.image

  environment = var.env_vars
  secrets     = var.secret_env_vars

  log_configuration = module.scaling_service.log_configuration
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.12.2"
  secrets   = var.secret_env_vars
  role_name = module.scaling_service.task_execution_role_name
}
