module "worker" {
  source = "../../../../infrastructure/modules/worker"

  name         = "${local.namespace}_${var.name}"
  service_name = var.name

  image = var.container_image

  env_vars = merge(
    {
      metrics_namespace = "${local.namespace}_${var.name}",
      queue_url         = module.input_queue.url,
    },
    var.env_vars,
  )

  secret_env_vars = var.secret_env_vars

  subnets = var.fargate_service_boilerplate.subnets

  cluster_name = var.fargate_service_boilerplate.cluster_name
  cluster_arn  = var.fargate_service_boilerplate.cluster_arn

  launch_type = "FARGATE"

  security_group_ids = concat(
    var.security_group_ids,
    [var.fargate_service_boilerplate.egress_security_group_id]
  )

  elastic_cloud_vpce_sg_id = var.fargate_service_boilerplate.elastic_cloud_vpce_security_group_id

  cpu    = var.cpu
  memory = var.memory

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.fargate_service_boilerplate.scale_down_adjustment
  scale_up_adjustment   = var.fargate_service_boilerplate.scale_up_adjustment

  shared_logging_secrets = var.fargate_service_boilerplate.shared_logging_secrets

  use_fargate_spot = var.use_fargate_spot
}
