locals {
  name = "${var.namespace}_${var.name}"
}

module "scaling_service" {
  source = "../../../../infrastructure/modules/scaling_service"

  name                         = local.name
  service_name                 = var.name
  cluster_name                 = var.cluster_name
  cluster_arn                  = var.cluster_arn
  subnets                      = var.subnets
  launch_type                  = var.launch_type
  desired_task_count           = var.desired_task_count
  capacity_provider_strategies = var.capacity_provider_strategies
  ordered_placement_strategies = var.ordered_placement_strategies

  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_security_group_id

  # Tasks using the EC2 launch type do not support EphemeralStorage
  ephemeral_storage_size = null

  security_group_ids = concat(
    var.security_group_ids,
    [var.egress_security_group_id]
  )

  volumes = var.volumes

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  cpu    = var.host_cpu
  memory = var.host_memory

  container_definitions = concat(
    local.app_container_definitions,
    [module.sidecar_container.container_definition]
  )

  queue_config = {
    name = "${local.name}_input"

    visibility_timeout_seconds = var.queue_visibility_timeout_seconds
    max_receive_count          = var.max_receive_count
    message_retention_seconds  = 4 * 24 * 60 * 60

    topic_arns = var.topic_arns

    dlq_alarm_arn                = var.dlq_alarm_topic_arn
    main_q_age_alarm_action_arns = var.main_q_age_alarm_action_arns

    cooldown_period = "1m"
  }

  shared_logging_secrets = var.shared_logging_secrets
}

locals {
  all_secret_env_vars       = merge(values(var.apps)[*].secret_env_vars...)
  app_container_definitions = values(module.app_container)[*].container_definition
}

module "app_container" {
  source   = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v4.3.0"
  for_each = var.apps

  name  = each.key
  image = each.value.image

  cpu    = each.value.cpu
  memory = each.value.memory

  environment = each.value.env_vars
  secrets     = each.value.secret_env_vars

  healthcheck = each.value.healthcheck

  log_configuration = module.scaling_service.log_configuration

  mount_points = each.value.mount_points
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.0"
  secrets   = local.all_secret_env_vars
  role_name = module.scaling_service.task_execution_role_name
}

module "sidecar_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v4.3.0"

  name  = var.manager_container_name
  image = var.manager_container_image

  cpu    = var.manager_cpu
  memory = var.manager_memory

  environment = merge(
    {
      metrics_namespace = local.name,
      queue_url         = module.scaling_service.queue_url,
    },
    var.manager_env_vars,
  )
  secrets = var.manager_secret_env_vars

  depends = [for name, app in var.apps : {
    containerName = name
    condition     = "HEALTHY"
  } if app.healthcheck != null]

  log_configuration = module.scaling_service.log_configuration

  mount_points = var.manager_mount_points
}

module "sidecar_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.0"
  secrets   = var.manager_secret_env_vars
  role_name = module.scaling_service.task_execution_role_name
}
