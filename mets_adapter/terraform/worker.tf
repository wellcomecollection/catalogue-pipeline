module "mets_adapter" {
  source = "../../pipeline/terraform/modules/fargate_service"

  name            = local.namespace
  container_image = local.mets_adapter_image

  topic_arns = [
    local.storage_notifications_topic_arn,
    module.repopulate_script_topic.arn,
  ]

  queue_name = "mets_adapter_queue"

  env_vars = {
    sns_arn = module.mets_adapter_output_topic.arn

    metrics_namespace         = local.namespace
    mets_adapter_dynamo_table = local.mets_adapter_table_name
    bag_api_url               = local.bag_api_url
    oauth_url                 = local.oauth_url

    # TODO: Change the METS adapter to look for the `queue_url` env var
    queue_id = module.mets_adapter.queue_url
  }

  omit_queue_url = true

  secret_env_vars = {
    oauth_client_id = "mets_adapter/mets_adapter/client_id"
    oauth_secret    = "mets_adapter/mets_adapter/secret"
  }

  min_capacity       = 0
  max_capacity       = 10
  desired_task_count = 0

  # TODO: Does the METS adapter need service discovery if it's the only
  # service in its cluster?
  service_discovery_namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  fargate_service_boilerplate = {
    cluster_name           = aws_ecs_cluster.cluster.name
    cluster_arn            = aws_ecs_cluster.cluster.arn
    subnets                = local.private_subnets
    shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging

    dlq_alarm_topic_arn = local.dlq_alarm_arn

    elastic_cloud_vpce_security_group_id = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id

    egress_security_group_id = aws_security_group.egress.id
    namespace                = local.namespace
  }
}
