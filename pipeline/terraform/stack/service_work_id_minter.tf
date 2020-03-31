module "work_id_minter_queue" {
  source          = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name      = "${local.namespace_hyphen}_work_id_minter"
  topic_arns      = [module.merger_works_topic.arn]
  aws_region      = var.aws_region
  alarm_topic_arn = var.dlq_alarm_arn
}

module "work_id_minter" {
  source          = "../modules/service"
  service_name    = "${local.namespace_hyphen}_work_id_minter"
  container_image = local.id_minter_image

  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
    var.rds_ids_access_security_group_id,
  ]

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  env_vars = {
    metrics_namespace    = "${local.namespace_hyphen}_work_id_minter"
    messages_bucket_name = aws_s3_bucket.messages.id

    queue_url       = module.work_id_minter_queue.url
    topic_arn       = module.work_id_minter_topic.arn
    max_connections = local.id_minter_task_max_connections

    logstash_host = local.logstash_host
  }

  secret_env_vars = {
    cluster_url = "catalogue/id_minter/rds_host"
    db_port     = "catalogue/id_minter/rds_port"
    db_username = "catalogue/id_minter/rds_user"
    db_password = "catalogue/id_minter/rds_password"
  }

  // The total number of connections to RDS across all tasks from all ID minter
  // services must not exceed the maximum supported by the RDS instance.
  max_capacity = floor(
    local.id_minter_rds_max_connections /
    (local.id_minter_service_count * local.id_minter_task_max_connections)
  )


  subnets             = var.subnets
  aws_region          = var.aws_region
  messages_bucket_arn = aws_s3_bucket.messages.arn
  queue_read_policy   = module.work_id_minter_queue.read_policy

  cpu    = 512
  memory = 1024
}

# Output topic

module "work_id_minter_topic" {
  source = "../modules/topic"

  name = "${local.namespace_hyphen}_work_id_minter"
  role_names = [
  module.work_id_minter.task_role_name]


  messages_bucket_arn = aws_s3_bucket.messages.arn
}

module "work_id_minter_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.work_id_minter_queue.name

  queue_high_actions = [module.work_id_minter.scale_up_arn]
  queue_low_actions  = [module.work_id_minter.scale_down_arn]
}
