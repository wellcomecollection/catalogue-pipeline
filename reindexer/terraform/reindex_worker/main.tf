module "reindex_worker" {
  source = "../../../pipeline/terraform/modules/fargate_service"

  name            = var.service_name
  container_image = var.reindex_worker_container_image

  topic_arns = [module.reindex_jobs_topic.arn]

  # Messages take a while to process in the reindexer.
  queue_visibility_timeout_seconds = 10 * 60
  queue_name                       = "reindex_worker_queue"

  env_vars = {
    metrics_namespace         = var.service_name
    reindexer_job_config_json = var.reindexer_job_config_json

    # TODO: Change the reindexer to look for the `queue_url` env var
    reindex_jobs_queue_id = module.reindex_worker.queue_url
  }

  omit_queue_url = true

  desired_task_count     = 0
  min_capacity           = 0
  max_capacity           = 7

  cpu    = 1024
  memory = 2048

  fargate_service_boilerplate = {
    cluster_name = var.cluster_name
    cluster_arn  = var.cluster_arn

    subnets = var.private_subnets

    dlq_alarm_topic_arn = var.dlq_alarm_arn

    shared_logging_secrets = var.shared_logging_secrets

    elastic_cloud_vpce_security_group_id = var.elastic_cloud_vpce_sg_id

    # TODO: Does the reindexer still need an egress security group?
    egress_security_group_id = var.service_egress_security_group_id
  }
}

moved {
  from = module.service
  to   = module.reindex_worker.module.worker
}

moved {
  from = module.reindexer_scaling
  to   = module.reindex_worker.module.scaling_alarm
}

moved {
  from = module.reindexer_queue
  to   = module.reindex_worker.module.input_queue
}

moved {
  from = aws_iam_role_policy.allow_queue_read
  to   = module.reindex_worker.aws_iam_role_policy.read_from_q
}
