locals {
  namespace = lookup(var.fargate_service_boilerplate, "namespace", "")

  default_queue_env_vars = var.omit_queue_url ? {} : { queue_url = module.scaling_service.queue_url }

  default_namespace_env_vars = local.namespace == "" ? {} : {
    metrics_namespace = "${local.namespace}_${var.name}"
  }

  name = local.namespace == "" ? var.name : "${local.namespace}_${var.name}"

  queue_name = var.queue_name == null ? trim("${local.namespace}_${var.name}_input", "_") : var.queue_name
}

module "scaling_service" {
  source = "../../../../infrastructure/modules/scaling_service"

  name = local.name

  shared_logging_secrets = var.fargate_service_boilerplate.shared_logging_secrets

  cluster_arn  = var.fargate_service_boilerplate.cluster_arn
  cluster_name = var.fargate_service_boilerplate.cluster_name
  service_name = var.name

  namespace_id = var.service_discovery_namespace_id

  launch_type      = "FARGATE"
  use_fargate_spot = var.use_fargate_spot

  subnets                  = var.fargate_service_boilerplate.subnets
  elastic_cloud_vpce_sg_id = var.fargate_service_boilerplate["elastic_cloud_vpce_security_group_id"]
  security_group_ids = concat(
    var.security_group_ids,
    lookup(var.fargate_service_boilerplate, "egress_security_group_id", "") != "" ? [var.fargate_service_boilerplate["egress_security_group_id"]] : []
  )

  desired_task_count = var.desired_task_count

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  queue_config = {
    name = local.queue_name

    topic_arns = var.topic_arns

    visibility_timeout_seconds = var.queue_visibility_timeout_seconds
    message_retention_seconds  = var.message_retention_seconds
    max_receive_count          = var.max_receive_count
    message_retention_seconds  = var.message_retention_seconds

    cooldown_period = var.cooldown_period

    dlq_alarm_arn                = var.fargate_service_boilerplate.dlq_alarm_topic_arn
    main_q_age_alarm_action_arns = var.fargate_service_boilerplate.main_q_age_alarm_action_arns
  }

  scale_down_adjustment = lookup(var.fargate_service_boilerplate, "scale_down_adjustment", -1)
  scale_up_adjustment   = lookup(var.fargate_service_boilerplate, "scale_up_adjustment", 1)

  cpu    = var.cpu
  memory = var.memory

  container_definitions = [
    module.app_container.container_definition,
  ]

  trigger_values = var.trigger_values
}

module "app_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v4.3.1"

  name  = local.name
  image = var.container_image

  environment = merge(
    local.default_queue_env_vars,
    local.default_namespace_env_vars,
    var.env_vars,
  )

  entrypoint = var.entrypoint
  command    = var.command

  secrets = var.secret_env_vars

  log_configuration = module.scaling_service.log_configuration
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v4.3.1"
  secrets   = var.secret_env_vars
  role_name = module.scaling_service.task_execution_role_name
}
