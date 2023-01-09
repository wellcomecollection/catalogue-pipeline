locals {
  namespace = lookup(var.fargate_service_boilerplate, "namespace", "")

  default_queue_env_vars = var.omit_queue_url ? {} : { queue_url = module.input_queue.url }

  default_namespace_env_vars = local.namespace == "" ? {} : {
    metrics_namespace = "${local.namespace}_${var.name}"
  }
}

module "worker" {
  source = "../../../../infrastructure/modules/worker"

  name         = local.namespace == "" ? var.name : "${local.namespace}_${var.name}"
  service_name = var.name

  namespace_id = var.service_discovery_namespace_id

  image = var.container_image

  env_vars = merge(
    local.default_queue_env_vars,
    local.default_namespace_env_vars,
    var.env_vars,
  )

  secret_env_vars = var.secret_env_vars

  subnets = var.fargate_service_boilerplate.subnets

  cluster_name = var.fargate_service_boilerplate.cluster_name
  cluster_arn  = var.fargate_service_boilerplate.cluster_arn

  launch_type = "FARGATE"

  security_group_ids = concat(
    var.security_group_ids,
    lookup(var.fargate_service_boilerplate, "egress_security_group_id", "") != "" ? [var.fargate_service_boilerplate["egress_security_group_id"]] : []
  )

  elastic_cloud_vpce_sg_id = var.fargate_service_boilerplate["elastic_cloud_vpce_security_group_id"]

  cpu    = var.cpu
  memory = var.memory

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  desired_task_count = var.desired_task_count

  scale_down_adjustment = lookup(var.fargate_service_boilerplate, "scale_down_adjustment", null)
  scale_up_adjustment   = lookup(var.fargate_service_boilerplate, "scale_up_adjustment", null)

  shared_logging_secrets = var.fargate_service_boilerplate.shared_logging_secrets

  use_fargate_spot = var.use_fargate_spot
}
