module "service" {
  source = "../../../infrastructure/modules/worker"

  name = var.service_name

  deployment_service_env  = var.service_env
  deployment_service_name = var.service_name

  image = var.reindex_worker_container_image

  security_group_ids = [var.service_egress_security_group_id]

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

  desired_task_count = 0
  min_capacity       = 0
  max_capacity       = 7
}

module "ingestor_works_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.3"
  queue_name = module.reindexer_queue.name

  queue_high_actions = [module.service.scale_up_arn]
  queue_low_actions  = [module.service.scale_down_arn]
}
