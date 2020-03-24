data "aws_ssm_parameter" "snapshot_generator_image" {
  provider = "aws.platform"

  name = "/catalogue_api/images/latest/snapshot_generator"
}

module "task_definition" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/single_container?ref=v1.5.2"

  task_name = "snapshot_generator"

  container_image = data.aws_ssm_parameter.snapshot_generator_image.value

  cpu    = 512
  memory = 1024

  env_vars = {
    queue_url        = var.input_queue_url
    topic_arn        = var.output_topic_arn
    metric_namespace = "snapshot_generator"
  }

  secret_env_vars = {
    es_host     = "catalogue/api/es_host"
    es_port     = "catalogue/api/es_port"
    es_protocol = "catalogue/api/es_protocol"
    es_username = "catalogue/api/es_username"
    es_password = "catalogue/api/es_password"
  }

  aws_region = var.aws_region
}

module "service" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.5.2"

  service_name = "snapshot_generator"
  cluster_arn  = var.cluster_arn

  task_definition_arn = module.task_definition.arn

  subnets = var.subnets

  namespace_id = var.namespace_id

  security_group_ids = var.security_group_ids

  use_fargate_spot = true
}

module "autoscaling_actions" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//autoscaling?ref=v1.5.2"

  name         = "snapshot_generator"
  cluster_name = var.cluster_name
  service_name = module.service.name
}

module "autoscaling_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs.git//autoscaling?ref=v1.1.2"

  queue_name = var.input_queue_name

  queue_low_actions = [
    module.autoscaling_actions.scale_down_arn,
  ]

  queue_high_actions = [
    module.autoscaling_actions.scale_up_arn,
  ]
}
