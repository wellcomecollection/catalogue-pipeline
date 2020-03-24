module "task_definition" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/single_container?ref=v1.5.2"

  task_name = var.name

  container_image = "wellcome/logstash_transit:edgelord"

  cpu    = 1024
  memory = 2048

  env_vars = {
    XPACK_MONITORING_ENABLED = "false"
    NAMESPACE                = var.namespace
  }

  secret_env_vars = {
    ES_HOST = "catalogue/logstash/es_host"
    ES_USER = "catalogue/logstash/es_user"
    ES_PASS = "catalogue/logstash/es_pass"
  }

  aws_region = var.aws_region
}

module "service" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.5.2"

  service_name = var.name
  cluster_arn  = var.cluster_arn

  desired_task_count = 1

  task_definition_arn = module.task_definition.arn

  subnets = var.subnets

  namespace_id = var.namespace_id

  security_group_ids = var.security_group_ids
}