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

  base_total_cpu           = 8192
  base_total_memory        = 15463
  base_manager_memory      = 2048
  base_manager_cpu         = 1024
  base_aspect_ratio_cpu    = 2048
  base_aspect_ratio_memory = 2048

  # When we're not reindexing, we halve the size of these tasks, because
  # they won't be getting as many updates.
  #
  # Note: while we're running the TEI on/TEI off pipeline, we run c5.2xlarge
  # instances when not reindexing, so we can run 2 tasks per instance.
  # This avoids some weird issues we've seen where an instance starts
  # but ECS is unable to place tasks anywhere.
  #
  # The problem:
  #
  #   - image update arrives
  #   - both tei-on and tei-off want to start an inferrer = 2 tasks
  #   - for some reason, the tasks consistently don't start
  #
  # We're hoping that using the same capacity provider and allowing
  # 2 tasks per instance will fix this issue.
  #
  # Once we're no longer running a split TEI pipeline, we should be able
  # to reduce the size of instances when we're not reindexing.
  #
  total_cpu           = var.is_reindexing ? local.base_total_cpu : floor(local.base_total_cpu / 2)
  total_memory        = var.is_reindexing ? local.base_total_memory : floor(local.base_total_memory / 2)
  manager_memory      = var.is_reindexing ? local.base_manager_memory : floor(local.base_manager_memory / 2)
  manager_cpu         = var.is_reindexing ? local.base_manager_cpu : floor(local.base_manager_cpu / 2)
  aspect_ratio_cpu    = var.is_reindexing ? local.base_aspect_ratio_cpu : floor(local.base_aspect_ratio_cpu / 2)
  aspect_ratio_memory = var.is_reindexing ? local.base_aspect_ratio_memory : floor(local.base_aspect_ratio_memory / 2)

  inferrer_cpu    = floor(0.5 * (local.total_cpu - local.manager_cpu - local.aspect_ratio_cpu))
  inferrer_memory = floor(0.5 * (local.total_memory - local.manager_memory - local.aspect_ratio_memory))
}

module "image_inferrer_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "${local.namespace}_image_inferrer_input"
  topic_arns                 = [module.merger_images_output_topic.arn]
  alarm_topic_arn            = var.dlq_alarm_arn
  visibility_timeout_seconds = local.queue_visibility_timeout
}
module "image_inferrer" {
  source = "../modules/services_with_manager"

  namespace = local.namespace
  name      = "image_inferrer"

  security_group_ids = [
    aws_security_group.service_egress.id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

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
        PORT              = local.feature_inferrer_port
        MODEL_OBJECT_KEY  = data.aws_ssm_parameter.inferrer_lsh_model_key.value
        MODEL_DATA_BUCKET = var.inferrer_model_data_bucket_name
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
    metrics_namespace          = "image_inferrer"
    topic_arn                  = module.image_inferrer_topic.arn
    queue_url                  = module.image_inferrer_queue.url
    images_root                = local.shared_storage_path

    es_initial_images_index   = local.es_images_initial_index
    es_augmented_images_index = local.es_images_augmented_index

    flush_interval_seconds = 30

    batch_size = 25
  }

  manager_secret_env_vars = local.pipeline_storage_es_service_secrets["inferrer"]

  subnets = var.subnets

  # Any higher than this currently causes latency spikes from Loris
  # TODO: Now these images are served by DLCS, not Loris, can we increase
  # the max capacity?
  min_capacity = var.min_capacity

  max_capacity = min(10, local.max_capacity)

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = min(1, local.scale_up_adjustment)

  queue_read_policy = module.image_inferrer_queue.read_policy

  deployment_service_env  = var.release_label
  deployment_service_name = "image-inferrer"
  shared_logging_secrets  = var.shared_logging_secrets
}

resource "aws_iam_role_policy" "read_inferrer_data" {
  role   = module.image_inferrer.task_role_name
  policy = data.aws_iam_policy_document.allow_inferrer_data_access.json
}

data "aws_iam_policy_document" "allow_inferrer_data_access" {
  statement {
    actions = [
      "s3:GetObject*",
      "s3:ListBucket",
    ]

    resources = [
      "arn:aws:s3:::${var.inferrer_model_data_bucket_name}",
      "arn:aws:s3:::${var.inferrer_model_data_bucket_name}/*",
    ]
  }
}

module "image_inferrer_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_image_inferrer_output"
  role_names = [module.image_inferrer.task_role_name]
}

module "image_inferrer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.image_inferrer_queue.name

  queue_high_actions = [module.image_inferrer.scale_up_arn]
  queue_low_actions  = [module.image_inferrer.scale_down_arn]
}
