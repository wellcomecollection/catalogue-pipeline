module "service" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/service?ref=v2.4.1"

  task_definition_arn            = module.task_definition.arn
  service_name                   = local.namespace
  cluster_arn                    = aws_ecs_cluster.cluster.arn
  subnets                        = local.private_subnets
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  launch_type                    = "FARGATE"
  desired_task_count             = 1
}

module "autoscaling" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/autoscaling?ref=v2.4.1"

  name = local.namespace

  cluster_name = local.namespace
  service_name = local.namespace

  min_capacity = 1
  max_capacity = 2
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v2.4.1"

  cpu    = 256
  memory = 512

  launch_types = ["FARGATE"]
  task_name    = local.namespace

  container_definitions = [
    module.log_router_container.container_definition,
    module.app_container.container_definition
  ]
}

module "app_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v2.4.1"

  name  = local.namespace
  image = local.calm_adapter_image

  environment = local.env_vars
  secrets     = local.secret_env_vars

  log_configuration = module.log_router_container.container_log_configuration
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v2.4.1"
  secrets   = local.secret_env_vars
  role_name = module.task_definition.task_execution_role_name
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v2.4.1"
  namespace = local.namespace
}

module "log_router_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v2.4.1"
  secrets   = local.shared_secrets_logging
  role_name = module.task_definition.task_execution_role_name
}
