module "service" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.2.0"
  service_name = local.namespace
  cluster_arn = aws_ecs_cluster.cluster.arn
  task_definition_arn = module.task_definition.arn
  subnets = local.private_subnets
  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  security_group_ids = []
  launch_type = "FARGATE"
  desired_task_count = 1
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/single_container?ref=v1.2.0"
  task_name = local.namespace
  container_image = local.calm_adapter_image
  cpu    = "256"
  memory = "512"
  env_vars        = local.env_vars
  secret_env_vars = local.secret_env_vars
  launch_type = "FARGATE"
  aws_region = local.aws_region
}

module "scaling" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//autoscaling?ref=v1.2.0"

  name = local.namespace

  cluster_name = local.namespace
  service_name = local.namespace

  min_capacity = 1
  max_capacity = 2
}
