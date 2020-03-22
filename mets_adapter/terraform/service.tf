locals {
  name = "mets_adapter"
}

module "task" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/single_container?ref=a1b65cf"

  task_name = local.name

  container_image = local.mets_adapter_image

  cpu    = 256
  memory = 512

  env_vars = {
    logstash_host             = local.logstash_host
    sns_arn                   = module.mets_adapter_topic.arn
    queue_id                  = module.queue.url
    metrics_namespace         = local.namespace
    mets_adapter_dynamo_table = local.mets_adapter_table_name
    bag_api_url               = local.bag_api_url
    oauth_url                 = local.oauth_url
  }

  secret_env_vars = {
    oauth_client_id = "mets_adapter/mets_adapter/client_id"
    oauth_secret    = "mets_adapter/mets_adapter/secret"
  }

  aws_region = local.aws_region
}

module "service" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.5.0"

  service_name = local.name

  cluster_arn = aws_ecs_cluster.cluster.arn

  desired_task_count = 0

  task_definition_arn = module.task.arn

  subnets = local.private_subnets

  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
}

module "autoscaling" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//autoscaling?ref=v1.5.0"

  name         = local.name
  cluster_name = aws_ecs_cluster.cluster.name
  service_name = local.name

  min_capacity = 0
  max_capacity = 10
}
