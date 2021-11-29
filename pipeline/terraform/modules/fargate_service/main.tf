module "worker" {
  source = "../../../../infrastructure/modules/worker"

  name         = "${var.namespace}_${var.name}"
  service_name = var.name

  image = var.container_image

  env_vars = merge(
    {
      metrics_namespace = "${var.namespace}_${var.name}",
      queue_url         = module.input_queue.url,
    },
    var.env_vars,
  )

  secret_env_vars = var.secret_env_vars

  subnets = var.subnets

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  launch_type = "FARGATE"

  security_group_ids = concat(
    var.security_group_ids,
    [var.egress_security_group_id]
  )

  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_security_group_id

  cpu    = var.cpu
  memory = var.memory

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = replace(var.name, "_", "-")

  shared_logging_secrets  = var.shared_logging_secrets

  use_fargate_spot = var.use_fargate_spot
}
