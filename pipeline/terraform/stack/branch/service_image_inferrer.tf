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

  total_cpu           = 8192
  total_memory        = 15463
  manager_memory      = 2048
  manager_cpu         = 1024
  aspect_ratio_cpu    = 2048
  aspect_ratio_memory = 2048
  inferrer_cpu        = floor(0.5 * (local.total_cpu - local.manager_cpu - local.aspect_ratio_cpu))
  inferrer_memory     = floor(0.5 * (local.total_memory - local.manager_memory - local.aspect_ratio_memory))
}

module "image_inferrer_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "${local.namespace}_image_inferrer"
  topic_arns                 = [module.merger_images_topic.arn]
  alarm_topic_arn            = var.dlq_alarm_arn
  visibility_timeout_seconds = local.queue_visibility_timeout
}
module "image_inferrer" {
  source = "../../modules/services_with_manager"

  service_name = "${local.namespace}_image_inferrer"
  security_group_ids = [
    var.service_egress_security_group_id,
  ]

  elastic_cloud_vpce_sg_id = var.ec_privatelink_security_group_id

  cluster_name = var.cluster_name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  launch_type = "EC2"
  capacity_provider_strategies = [{
    capacity_provider = var.inference_capacity_provider_name
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
  manager_container_image = var.inference_manager_image
  manager_cpu             = local.manager_cpu
  manager_memory          = local.manager_memory
  manager_mount_points = [{
    containerPath = local.shared_storage_path,
    sourceVolume  = local.shared_storage_name
  }]

  apps = {
    feature_inferrer = {
      image  = var.feature_inferrer_image
      cpu    = local.inferrer_cpu
      memory = local.inferrer_memory
      env_vars = {
        PORT              = local.feature_inferrer_port
        MODEL_OBJECT_KEY  = var.inferrer_lsh_model_key_value
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
      image  = var.palette_inferrer_image
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
      image  = var.aspect_ratio_inferrer_image
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
    metrics_namespace          = "${local.namespace}_image_inferrer"
    topic_arn                  = module.image_inferrer_topic.arn
    queue_url                  = module.image_inferrer_queue.url
    images_root                = local.shared_storage_path

    es_initial_images_index   = local.es_images_initial_index
    es_augmented_images_index = local.es_images_augmented_index

    flush_interval_seconds = 30

    batch_size = 25
  }

  manager_secret_env_vars = var.pipeline_storage_es_service_secrets["inferrer"]

  subnets = var.subnets

  # Any higher than this currently causes latency spikes from Loris
  # TODO: Now these images are served by DLCS, not Loris, can we increase
  # the max capacity?
  min_capacity = var.min_capacity

  # Note: this slightly arcane construction essentially says: if the
  # namespace contains "tei-on", disable the image inferrer.
  #
  # This is because of some sort of weird interaction between the image
  # inferrers and the EC2 capacity provider.  When an image update
  # arrives in the pipeline, both inferrer services get desiredCapacity=1,
  # the EC2 capacity provider starts 1 instance, and neither task is
  # able to start running successfully.
  #
  # Disabling the image inferrer in the "tei-on" pipeline is a hack
  # to fix the image inferrer in the publicly visible pipeline.
  # The "tei-off" inferrer gets exclusive use of the EC2 instance and is
  # able to start correctly.
  #
  # At some point it'd be nice to come back and sort this out properly,
  # by understanding exactly how we've misconfigured ECS/EC2, but I don't
  # have time to do that right now.
  #
  # When we bin the split TEI on/off pipelines, delete everything
  # before the colon.
  max_capacity = length(regexall("tei-on", local.namespace)) > 0 ? 0 : min(6, var.max_capacity)

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = min(1, var.scale_up_adjustment)

  queue_read_policy = module.image_inferrer_queue.read_policy

  depends_on = [
    var.elasticsearch_users,
  ]

  deployment_service_env  = var.release_label
  deployment_service_name = "image-inferrer-${local.tei_suffix}"
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
  source = "../../modules/topic"

  name       = "${local.namespace}_image_inferrer"
  role_names = [module.image_inferrer.task_role_name]
}

module "image_inferrer_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.image_inferrer_queue.name

  queue_high_actions = [module.image_inferrer.scale_up_arn]
  queue_low_actions  = [module.image_inferrer.scale_down_arn]
}
