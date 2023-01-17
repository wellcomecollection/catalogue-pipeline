locals {
  # Allow overriding the ECS service name
  #
  # This is used to set a service name that isn't prefixed by the namespace,
  # e.g. "id_minter" instead of "catalogue-2021-09-23-id_minter".
  #
  # This makes it easier to identify services in contexts where the service
  # name is truncated, like the logging cluster or the ECS console.
  service_name = var.service_name == "" ? var.name : var.service_name
}

module "service" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/service?ref=v3.13.0"

  task_definition_arn            = module.task_definition.arn
  service_name                   = local.service_name
  cluster_arn                    = var.cluster_arn
  subnets                        = var.subnets
  service_discovery_namespace_id = var.namespace_id
  launch_type                    = var.launch_type
  desired_task_count             = var.desired_task_count
  use_fargate_spot               = var.use_fargate_spot
  capacity_provider_strategies   = var.capacity_provider_strategies
  ordered_placement_strategies   = var.ordered_placement_strategies

  # We need to append the Elastic Cloud VPC endpoint security group so
  # that our services can talk to the logging cluster.
  security_group_ids = concat(
    var.security_group_ids,
    [var.elastic_cloud_vpce_sg_id]
  )

  propagate_tags = "SERVICE"
}

module "autoscaling" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/autoscaling?ref=v3.12.2"

  name = var.name

  cluster_name = var.cluster_name
  service_name = module.service.name

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v3.12.2"

  cpu    = var.cpu
  memory = var.memory

  launch_types = [var.launch_type]
  task_name    = var.name

  volumes = var.volumes

  container_definitions = concat(
    [module.log_router_container.container_definition],
    var.container_definitions
  )
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v3.12.2"
  namespace = var.name

  use_privatelink_endpoint = true
}

module "log_router_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.12.2"
  secrets   = var.shared_logging_secrets
  role_name = module.task_definition.task_execution_role_name
}
