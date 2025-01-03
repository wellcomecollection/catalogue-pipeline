locals {
  feature_inferrer_port      = 3141
  palette_inferrer_port      = 3142
  aspect_ratio_inferrer_port = 3143
  //  High inferrer throughput comes at the cost of the latency distribution
  // having heavy tails - this stops some unfortunate messages from being
  // put on the DLQ when they are consumed but not processed.
  queue_visibility_timeout = 60
  shared_storage_name      = "shared_storage"
  shared_storage_path      = "/data"

  # This is the CPU/memory available on an ECS instance which isn't running
  # any tasks.  You can find it in the ECS console, in the list of
  # capacity providers.
  base_2x_total_cpu = 8192
  base_1x_total_cpu = 4096

  base_2x_total_memory = 15463
  base_1x_total_memory = 7611

  base_manager_memory      = 2048
  base_manager_cpu         = 1024
  base_aspect_ratio_cpu    = 2048
  base_aspect_ratio_memory = 2048

  # When we're not reindexing, we halve the size of these tasks, because
  # they won't be getting as many updates.
  total_cpu           = var.reindexing_state.scale_up_tasks ? local.base_2x_total_cpu : local.base_1x_total_cpu
  total_memory        = var.reindexing_state.scale_up_tasks ? local.base_2x_total_memory : local.base_1x_total_memory
  manager_memory      = var.reindexing_state.scale_up_tasks ? local.base_manager_memory : floor(local.base_manager_memory / 2)
  manager_cpu         = var.reindexing_state.scale_up_tasks ? local.base_manager_cpu : floor(local.base_manager_cpu / 2)
  aspect_ratio_cpu    = var.reindexing_state.scale_up_tasks ? local.base_aspect_ratio_cpu : floor(local.base_aspect_ratio_cpu / 2)
  aspect_ratio_memory = var.reindexing_state.scale_up_tasks ? local.base_aspect_ratio_memory : floor(local.base_aspect_ratio_memory / 2)

  log_router_memory = 50

  inferrer_cpu    = floor(0.5 * (local.total_cpu - local.manager_cpu - local.aspect_ratio_cpu))
  inferrer_memory = floor(0.5 * (local.total_memory - local.manager_memory - local.aspect_ratio_memory - local.log_router_memory))
}

module "image_inferrer" {
  source = "../services_with_manager"

  name = "image_inferrer"

  topic_arns = [
    module.merger_images_output_topic.arn,
  ]

  queue_visibility_timeout_seconds = local.queue_visibility_timeout

  launch_type = "EC2"
  capacity_provider_strategies = [{
    capacity_provider = module.inference_capacity_provider.name
    weight            = 1
  }]
  ordered_placement_strategies = [{
    type  = "spread"
    field = "host"
  }]
  volumes = [{
    name      = local.shared_storage_name,
    host_path = null
  }]

  host_cpu    = null
  host_memory = null

  manager_container_name  = "inference_manager"
  manager_container_image = local.inference_manager_image
  manager_cpu             = local.manager_cpu
  manager_memory          = local.manager_memory
  manager_mount_points = [{
    containerPath = local.shared_storage_path,
    sourceVolume  = local.shared_storage_name
  }]

  apps = {
    feature_inferrer = {
      image  = local.feature_inferrer_image
      cpu    = local.inferrer_cpu
      memory = local.inferrer_memory
      env_vars = {
        PORT = local.feature_inferrer_port
      }
      secret_env_vars = {}
      mount_points = [{
        containerPath = local.shared_storage_path,
        sourceVolume  = local.shared_storage_name
      }]
      healthcheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${local.feature_inferrer_port}/healthcheck"],
        interval    = 30,
        retries     = 3,
        startPeriod = 30,
        timeout     = 5
      }
    }
    palette_inferrer = {
      image  = local.palette_inferrer_image
      cpu    = local.inferrer_cpu
      memory = local.inferrer_memory
      env_vars = {
        PORT = local.palette_inferrer_port
      }
      secret_env_vars = {}
      mount_points = [{
        containerPath = local.shared_storage_path,
        sourceVolume  = local.shared_storage_name
      }]
      healthcheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${local.palette_inferrer_port}/healthcheck"],
        interval    = 30,
        retries     = 3,
        startPeriod = 30,
        timeout     = 5
      }
    }
    aspect_ratio_inferrer = {
      image  = local.aspect_ratio_inferrer_image
      cpu    = local.aspect_ratio_cpu
      memory = local.aspect_ratio_memory
      env_vars = {
        PORT = local.aspect_ratio_inferrer_port
      }
      secret_env_vars = {}
      mount_points = [{
        containerPath = local.shared_storage_path,
        sourceVolume  = local.shared_storage_name
      }]
      healthcheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${local.aspect_ratio_inferrer_port}/healthcheck"],
        interval    = 30,
        retries     = 3,
        startPeriod = 30,
        timeout     = 5
      }
    }
  }

  manager_env_vars = {
    feature_inferrer_host      = "localhost"
    feature_inferrer_port      = local.feature_inferrer_port
    palette_inferrer_host      = "localhost"
    palette_inferrer_port      = local.palette_inferrer_port
    aspect_ratio_inferrer_host = "localhost"
    aspect_ratio_inferrer_port = local.aspect_ratio_inferrer_port
    topic_arn                  = module.image_inferrer_output_topic.arn
    images_root                = local.shared_storage_path

    es_initial_images_index   = local.es_images_initial_index
    es_augmented_images_index = local.es_images_augmented_index

    flush_interval_seconds = 30

    batch_size = 25
  }

  manager_secret_env_vars = local.pipeline_storage_es_service_secrets["inferrer"]

  # Any higher than this currently causes latency spikes from Loris
  # TODO: Now these images are served by DLCS, not Loris, can we increase
  # the max capacity?
  min_capacity = var.min_capacity
  max_capacity = min(10, local.max_capacity)

  # Below this line is boilerplate that should be the same across
  # all services.
  egress_security_group_id             = aws_security_group.egress.id
  elastic_cloud_vpce_security_group_id = local.network_config.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  dlq_alarm_topic_arn          = local.monitoring_config.dlq_alarm_arn
  main_q_age_alarm_action_arns = local.monitoring_config.main_q_age_alarm_action_arns

  subnets = local.network_config.subnets

  namespace = local.namespace

  shared_logging_secrets = local.monitoring_config.shared_logging_secrets
}

module "image_inferrer_output_topic" {
  source = "../topic"

  name       = "${local.namespace}_image_inferrer_output"
  role_names = [module.image_inferrer.task_role_name]
}
