module "tei_adapter_w" {
  source = "../../pipeline/terraform/modules/fargate_service"

  name            = "tei_adapter"
  container_image = local.tei_adapter_image

  topic_arns = [module.tei_id_extractor_topic.arn]

  queue_name                       = "tei-adapter"
  queue_visibility_timeout_seconds = 60
  message_retention_seconds        = 4 * 24 * 60 * 60

  env_vars = {
    metrics_namespace        = "${local.namespace}_tei_adapter"
    topic_arn                = module.tei_adapter_topic.arn
    parallelism              = 10
    delete_delay             = "2 minutes"
    tei_adapter_dynamo_table = aws_dynamodb_table.tei_adapter_table.id
  }

  cpu    = 1024
  memory = 2048

  min_capacity = local.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = {
    cluster_name           = aws_ecs_cluster.cluster.name
    cluster_arn            = aws_ecs_cluster.cluster.arn
    subnets                = local.private_subnets
    shared_logging_secrets = local.shared_logging_secrets

    dlq_alarm_topic_arn = local.dlq_alarm_arn

    elastic_cloud_vpce_security_group_id = local.elastic_cloud_vpce_sg_id

    egress_security_group_id = aws_security_group.egress.id
    namespace                = local.namespace
  }

  security_group_ids = [
    aws_security_group.rds_ingress_security_group.id
  ]
}

resource "aws_iam_role_policy" "tei_adapter_publish_policy" {
  role   = module.tei_adapter_w.task_role_name
  policy = module.tei_adapter_topic.publish_policy
}

resource "aws_iam_role_policy" "tei_adapter_dynamo_full_access" {
  role   = module.tei_adapter_w.task_role_name
  policy = data.aws_iam_policy_document.tei_dynamo_full_access_policy.json
}
