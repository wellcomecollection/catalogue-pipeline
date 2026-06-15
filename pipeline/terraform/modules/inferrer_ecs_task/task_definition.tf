# An EC2, multi-container task definition for image inference: the Python
# inference_manager plus the three inferrer sidecars, sharing a scratch volume.
# Modelled on ../ecs_task (Fargate, single container) but for the EC2 capacity
# provider and multiple containers. Launched by the image-inferrer state machine
# via ecs:runTask.waitForTaskToken; the manager command is set by the state
# machine through container overrides.

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v4.3.1"
  namespace = var.task_name

  use_privatelink_endpoint = true
}

module "manager_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v4.3.1"
  name   = var.task_name
  image  = var.manager_image

  cpu    = var.manager_cpu
  memory = var.manager_memory

  environment  = var.manager_env_vars
  mount_points = var.manager_mount_points

  # Wait for every inferrer to be healthy before the manager starts calling them.
  depends = [
    for name, app in var.apps : {
      containerName = name
      condition     = "HEALTHY"
    } if app.healthcheck != null
  ]

  log_configuration = module.log_router_container.container_log_configuration
}

module "app_container" {
  source   = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v4.3.1"
  for_each = var.apps

  name  = each.key
  image = each.value.image

  cpu    = each.value.cpu
  memory = each.value.memory

  environment = each.value.env_vars
  secrets     = each.value.secret_env_vars

  healthcheck  = each.value.healthcheck
  mount_points = each.value.mount_points

  log_configuration = module.log_router_container.container_log_configuration
}

module "log_router_container_secrets_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.1"
  secrets   = module.log_router_container.shared_secrets_logging
  role_name = module.task_definition.task_execution_role_name
}

module "app_secrets_permissions" {
  source   = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.1"
  for_each = var.apps

  secrets   = each.value.secret_env_vars
  role_name = module.task_definition.task_execution_role_name
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v4.3.1"

  # Container-level cpu/memory reservations are set per container above; the task
  # itself does not pin cpu/memory (EC2 launch type). Ephemeral storage is a
  # Fargate-only concept, so it is excluded.
  cpu                    = null
  memory                 = null
  ephemeral_storage_size = null

  launch_types = ["EC2"]
  network_mode = "awsvpc"

  volumes = var.volumes

  container_definitions = concat(
    [
      module.log_router_container.container_definition,
      module.manager_container.container_definition,
    ],
    [for name, container in module.app_container : container.container_definition],
  )

  task_name = var.task_name
}
