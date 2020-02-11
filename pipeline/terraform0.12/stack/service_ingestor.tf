# Input queue

module "ingestor_queue" {
  source      = "../modules/queue"
  name = "${local.namespace_hyphen}-ingestor"
  aws_region      = var.aws_region
  dlq_alarm_arn = var.dlq_alarm_arn
  role_names = [module.ingestor.task_role_name]
  topic_arns = [module.id_minter_topic.arn]
}

module "ingestor" {
  source = "../modules/service/pipeline_service"
  service_name = "${local.namespace_hyphen}_ingestor"
  container_image = local.ingestor_image

  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id,
  ]

  cluster_name  = aws_ecs_cluster.cluster.name
  cluster_arn    = aws_ecs_cluster.cluster.arn
  namespace_id  = aws_service_discovery_private_dns_namespace.namespace.id
  subnets       = var.subnets

  env_vars = {
    metrics_namespace   = "${local.namespace_hyphen}_ingestor"
    es_index            = var.es_works_index
    ingest_queue_id     = module.ingestor_queue.arn
    es_ingest_batchSize = 100
    logstash_host = local.logstash_host
  }

  secret_env_vars = {
    es_host     = "catalogue/ingestor/es_host"
    es_port     = "catalogue/ingestor/es_port"
    es_username = "catalogue/ingestor/es_username"
    es_password = "catalogue/ingestor/es_password"
    es_protocol = "catalogue/ingestor/es_protocol"
  }
  max_capacity           = 10

  messages_bucket_arn    = aws_s3_bucket.messages.arn
  queue_read_policy      = module.ingestor_queue.read_policy
}

// TODO: (tf0.12 upgrade) do we need this?
//module "ingestor_scaling_alarm" {
//  source     = "git::https://github.com/wellcometrust/terraform-modules.git//autoscaling/alarms/queue?ref=v19.12.0"
//  queue_name = module.ingestor_queue.name
//
//  queue_high_actions = [module.ingestor.scale_up_arn]
//  queue_low_actions  = [module.ingestor.scale_down_arn]
//}
