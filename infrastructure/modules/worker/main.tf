locals {
  deployment_service_name = var.deployment_service_name == "" ? var.name : var.deployment_service_name
}

module "service" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/service?ref=v3.0.0"

  task_definition_arn            = module.task_definition.arn
  service_name                   = var.name
  cluster_arn                    = var.cluster_arn
  subnets                        = var.subnets
  service_discovery_namespace_id = var.namespace_id
  launch_type                    = "FARGATE"
  desired_task_count             = var.desired_task_count
  security_group_ids             = var.security_group_ids
  use_fargate_spot               = var.use_fargate_spot

  propagate_tags = "SERVICE"

  tags = {
    "deployment:service": local.deployment_service_name
    "deployment:env": var.deployment_service_env
  }
}

module "autoscaling" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/autoscaling?ref=v3.0.0"

  name = var.name

  cluster_name = var.cluster_name
  service_name = var.name

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v3.0.0"

  cpu    = var.cpu
  memory = var.memory

  launch_types = ["FARGATE"]
  task_name    = var.name

  container_definitions = [
    module.log_router_container.container_definition,
    module.app_container.container_definition
  ]
}

module "app_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.0.0"

  name  = var.name
  image = var.image

  cpu    = var.app_cpu
  memory = var.app_memory

  environment = var.env_vars
  secrets     = var.secret_env_vars

  log_configuration = module.log_router_container.container_log_configuration
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.0.0"
  secrets   = var.secret_env_vars
  role_name = module.task_definition.task_execution_role_name
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v3.0.0"
  namespace = var.name
}

module "log_router_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.0.0"
  secrets   = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
  role_name = module.task_definition.task_execution_role_name
}

data "terraform_remote_state" "shared_infra" {
  backend = "s3"

  config = {
    role_arn = "arn:aws:iam::760097843905:role/platform-read_only"

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/shared.tfstate"
    region = "eu-west-1"
  }
}
