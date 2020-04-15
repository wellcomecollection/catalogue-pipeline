module "service" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.2.0"

  service_name = var.service_name

  cluster_arn = var.cluster_arn

  task_definition_arn = module.task_definition.arn

  subnets = var.subnets

  namespace_id = var.namespace_id

  security_group_ids = var.security_group_ids

  launch_type = var.launch_type

  desired_task_count = var.desired_task_count
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/container_with_sidecar?ref=v1.7.0"

  task_name = var.service_name

  app_container_image  = var.app_container_image
  app_container_name   = var.app_container_name
  app_container_port   = var.app_container_port
  app_cpu              = var.app_cpu
  app_memory           = var.app_memory
  app_env_vars         = var.app_env_vars
  secret_app_env_vars  = var.secret_app_env_vars
  app_healthcheck_json = var.app_healthcheck_json

  sidecar_container_image          = var.manager_container_image
  sidecar_container_name           = var.manager_container_name
  sidecar_cpu                      = var.manager_cpu
  sidecar_memory                   = var.manager_memory
  sidecar_env_vars                 = var.manager_env_vars
  secret_sidecar_env_vars          = var.secret_manager_env_vars
  sidecar_depends_on_app_condition = var.app_healthcheck_json == "" ? "" : "HEALTHY"

  cpu    = var.host_cpu
  memory = var.host_memory

  launch_type = var.launch_type

  aws_region = "eu-west-1"
}


module "scaling" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//autoscaling?ref=v1.2.0"

  name = var.service_name

  cluster_name = var.cluster_name
  service_name = var.service_name

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
}
