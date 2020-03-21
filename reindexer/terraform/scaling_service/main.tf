module "appautoscaling" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//autoscaling?ref=v1.5.0"

  name   = module.service.name

  cluster_name = var.cluster_name
  service_name = module.service.name

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
}

module "service" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.5.0"

  service_name = var.service_name
  cluster_arn  = var.cluster_arn

  desired_task_count = var.desired_task_count

  task_definition_arn = module.task.arn

  subnets = var.subnets

  namespace_id = var.namespace_id

  security_group_ids = var.security_group_ids

  launch_type = "FARGATE"
}

module "task" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/single_container?ref=a1b65cf8662f9ca4846b1b21c9172f8e8c07eee3"

  task_name = var.service_name

  container_image = var.container_image

  cpu    = var.cpu
  memory = var.memory

  env_vars = var.env_vars

  aws_region = var.aws_region
}
