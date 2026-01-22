module "tei_id_extractor_w" {
  source = "../../pipeline/terraform/modules/fargate_service"

  name            = "tei_id_extractor"
  container_image = local.tei_id_extractor_image

  topic_arns = [module.tei_updater_lambda.topic_arn]

  queue_name                       = "tei-id-extractor"
  queue_visibility_timeout_seconds = local.rds_lock_timeout_seconds + 30

  env_vars = {
    metrics_namespace = "${local.namespace}_tei_id_extractor"
    topic_arn         = module.tei_id_extractor_topic.arn
    bucket            = aws_s3_bucket.tei_adapter.id
    parallelism       = 10
    max_connections   = local.tei_id_extractor_max_connections
    delete_delay      = "30 minutes"
    database          = "pathid"
    table             = "pathid"
  }

  secret_env_vars = {
    db_host      = "rds/tei-adapter-cluster-serverless/endpoint"
    db_port      = "rds/tei-adapter-cluster-serverless/port"
    db_username  = "catalogue/tei_id_extractor/rds_user"
    db_password  = "catalogue/tei_id_extractor/rds_password"
    github_token = "catalogue/tei_id_extractor/github_token"
  }

  // The total number of connections to RDS across all tasks
  // must not exceed the maximum supported by the RDS instance.
  min_capacity = local.min_capacity
  max_capacity = min(floor(local.rds_max_connections / local.tei_id_extractor_max_connections), local.max_capacity)

  cpu    = 1024
  memory = 2048

  fargate_service_boilerplate = {
    cluster_name           = aws_ecs_cluster.cluster.name
    cluster_arn            = aws_ecs_cluster.cluster.arn
    subnets                = local.private_subnets
    shared_logging_secrets = local.shared_logging_secrets

    dlq_alarm_topic_arn = local.dlq_alarm_arn

    elastic_cloud_vpce_security_group_id = local.elastic_cloud_vpce_sg_id

    egress_security_group_id = aws_security_group.egress.id
    namespace = local.namespace
  }

  security_group_ids = [
    aws_security_group.rds_ingress_security_group.id
  ]
}

resource "aws_iam_role_policy" "tei_id_extractor_publish_policy" {
  role   = module.tei_id_extractor_w.task_role_name
  policy = module.tei_id_extractor_topic.publish_policy
}

resource "aws_iam_role_policy" "tei_id_extractor_put_policy" {
  role   = module.tei_id_extractor_w.task_role_name
  policy = data.aws_iam_policy_document.allow_s3_read_write.json
}
