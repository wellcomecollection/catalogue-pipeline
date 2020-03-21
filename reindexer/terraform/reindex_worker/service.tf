module "service" {
  source       = "../scaling_service"
  service_name = var.namespace

  desired_task_count = 0
  source_queue_name  = module.reindex_worker_queue.name
  source_queue_arn   = module.reindex_worker_queue.arn

  container_image    = var.reindex_worker_container_image
  security_group_ids = [var.service_egress_security_group_id]

  cpu    = 1024
  memory = 2048

  env_vars = {
    reindex_jobs_queue_id     = module.reindex_worker_queue.url
    metrics_namespace         = var.namespace
    reindexer_job_config_json = var.reindexer_job_config_json
  }

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn
  vpc_id       = var.vpc_id

  aws_region = var.aws_region
  subnets    = var.private_subnets

  namespace_id = var.namespace_id

  launch_type = "FARGATE"

  max_capacity = 7
}
