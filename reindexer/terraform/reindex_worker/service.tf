module "service" {
  source = "../../../infrastructure/modules/worker"

  name = var.service_name

  image = var.reindex_worker_container_image

  security_group_ids = [
    # TODO: Does the reindexer still need an egress security group?
    var.service_egress_security_group_id,
  ]

  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_sg_id

  cpu    = 1024
  memory = 2048

  env_vars = {
    reindex_jobs_queue_id     = module.reindexer_queue.url
    metrics_namespace         = var.service_name
    reindexer_job_config_json = var.reindexer_job_config_json
  }

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  subnets = var.private_subnets

  desired_task_count     = 0
  min_capacity           = 0
  max_capacity           = 7
  shared_logging_secrets = var.shared_logging_secrets

  use_fargate_spot = true
}

module "reindexer_scaling" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.reindexer_queue.name

  queue_high_actions = [module.service.scale_up_arn]
  queue_low_actions  = [module.service.scale_down_arn]
}
