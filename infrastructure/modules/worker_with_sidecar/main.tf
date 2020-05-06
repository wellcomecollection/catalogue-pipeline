module "worker" {
  source = "../worker"

  name  = var.name

  cpu             = var.cpu
  memory          = var.memory

  cluster_name       = var.cluster_name
  cluster_arn        = var.cluster_arn
  subnets            = var.subnets
  namespace_id       = var.namespace_id
  security_group_ids = var.security_group_ids
  use_fargate_spot   = var.use_fargate_spot

  image           = var.app_image
  port            = var.app_port
  env_vars        = var.app_env_vars
  secret_env_vars = var.app_secret_env_vars
  app_cpu         = var.app_cpu
  app_memory      = var.app_cpu

  desired_task_count = var.desired_task_count
  min_capacity       = var.min_capacity
  max_capacity       = var.max_capacity

  extra_container_definitions = [
    module.sidecar_container.container_definition
  ]
}

module "sidecar_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v2.4.1"

  name  = var.sidecar_name
  image = var.sidecar_image

  cpu    = var.sidecar_cpu
  memory = var.sidecar_memory

  environment = var.sidecar_env_vars
  secrets     = var.sidecar_secret_env_vars

  depends = var.app_healthcheck == "" ? [] : [{
    containerName = var.name
    condition     = "HEALTHY"
  }]
}

module "sidecar_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v2.4.1"
  secrets   = var.sidecar_secret_env_vars
  role_name = module.worker.task_execution_role_name
}
