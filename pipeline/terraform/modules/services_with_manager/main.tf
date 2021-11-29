module "worker" {
  source = "../../../../infrastructure/modules/workers_with_sidecar"

  name         = "${var.namespace}_${var.name}"
  service_name = var.name

  subnets = var.subnets

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  launch_type                  = var.launch_type
  capacity_provider_strategies = var.capacity_provider_strategies
  ordered_placement_strategies = var.ordered_placement_strategies
  volumes                      = var.volumes

  desired_task_count = var.desired_task_count

  security_group_ids       = concat(var.security_group_ids, [var.egress_security_group_id])
  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_security_group_id

  apps = var.apps

  sidecar_image           = var.manager_container_image
  sidecar_name            = var.manager_container_name
  sidecar_cpu             = var.manager_cpu
  sidecar_memory          = var.manager_memory
  sidecar_env_vars        = merge(
    {
      metrics_namespace = "${var.namespace}_${var.name}",
      queue_url         = module.input_queue.url,
    },
    var.manager_env_vars,
  )
  sidecar_secret_env_vars = var.manager_secret_env_vars
  sidecar_mount_points    = var.manager_mount_points

  cpu    = var.host_cpu
  memory = var.host_memory

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = replace(var.name, "_", "-")

  shared_logging_secrets = var.shared_logging_secrets
}
