module "tei_id_extractor_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "tei-id-extractor"
  topic_arns                 = [module.tei_updater_lambda.topic_arn]
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = local.rds_lock_timeout_seconds + 30
}

module "tei_id_extractor" {
  source = "../../infrastructure/modules/worker"

  name = "tei_id_extractor"

  image = local.tei_id_extractor_image

  env_vars = {
    metrics_namespace = "${local.namespace}_tei_id_extractor"
    queue_url         = module.tei_id_extractor_queue.url
    topic_arn         = module.tei_id_extractor_topic.arn
    bucket            = aws_s3_bucket.tei_adapter.id
    parallelism       = 10
    max_connections   = local.tei_id_extractor_max_connections
    delete_delay      = "30 minutes"
    database          = "pathid"
    table             = "pathid"
  }

  secret_env_vars = {
    db_host      = "rds/tei-adapter-cluster/endpoint"
    db_port      = "rds/tei-adapter-cluster/port"
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

  cluster_name             = aws_ecs_cluster.cluster.name
  cluster_arn              = aws_ecs_cluster.cluster.arn
  subnets                  = local.private_subnets
  shared_logging_secrets   = local.shared_logging_secrets
  elastic_cloud_vpce_sg_id = local.elastic_cloud_vpce_sg_id

  security_group_ids = [
    aws_security_group.egress.id,
    aws_security_group.rds_ingress_security_group.id
  ]

  use_fargate_spot = true
}

resource "aws_iam_role_policy" "read_from_extractor_queue" {
  role   = module.tei_id_extractor.task_role_name
  policy = module.tei_id_extractor_queue.read_policy
}

resource "aws_iam_role_policy" "tei_id_extractor_publish_policy" {
  role   = module.tei_id_extractor.task_role_name
  policy = module.tei_id_extractor_topic.publish_policy
}

resource "aws_iam_role_policy" "tei_id_extractor_put_policy" {
  role   = module.tei_id_extractor.task_role_name
  policy = data.aws_iam_policy_document.allow_s3_read_write.json
}

module "tei_id_extractor_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.tei_id_extractor_queue.name

  queue_high_actions = [
    module.tei_id_extractor.scale_up_arn
  ]

  queue_low_actions = [
    module.tei_id_extractor.scale_down_arn
  ]
}
