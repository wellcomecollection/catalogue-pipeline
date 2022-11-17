module "reindex_worker" {
  source = "./reindex_worker"

  reindexer_jobs            = local.reindexer_jobs
  reindexer_job_config_json = local.reindex_job_config_json

  reindex_worker_container_image = local.reindex_worker_image

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.arn

  service_egress_security_group_id = aws_security_group.service_egress_security_group.id
  elastic_cloud_vpce_sg_id         = data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_sg_id"]

  account_id = data.aws_caller_identity.current.account_id
  aws_region = data.aws_region.current.name

  private_subnets = local.private_subnets
  dlq_alarm_arn   = local.dlq_alarm_arn

  service_name = "reindexer"

  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
}
