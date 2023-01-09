resource "aws_iam_role_policy" "cloudwatch_push_metrics" {
  role   = module.task_definition.task_role_name
  policy = data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json
}

data "aws_iam_policy_document" "allow_cloudwatch_push_metrics" {
  statement {
    actions = [
      "cloudwatch:PutMetricData",
    ]

    resources = [
      "*",
    ]
  }
}


locals {
  all_secret_env_vars       = merge(values(var.apps)[*].secret_env_vars...)
  app_container_definitions = values(module.app_container)[*].container_definition
  other_container_definitions = [
    module.log_router_container.container_definition,
    module.sidecar_container.container_definition
  ]
}

module "service" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/service?ref=v3.13.0"

  task_definition_arn            = module.task_definition.arn
  service_name                   = var.name
  cluster_arn                    = var.cluster_arn
  subnets                        = var.subnets
  launch_type                    = var.launch_type
  desired_task_count             = var.desired_task_count
  capacity_provider_strategies   = var.capacity_provider_strategies
  ordered_placement_strategies   = var.ordered_placement_strategies

  # We need to append the Elastic Cloud VPC endpoint security group so
  # that our services can talk to the logging cluster.
  security_group_ids = concat(
    var.security_group_ids,
    [var.egress_security_group_id, var.elastic_cloud_vpce_security_group_id]
  )
}

module "autoscaling" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/autoscaling?ref=v3.12.2"

  name = "${var.namespace}_${var.name}"

  cluster_name = var.cluster_name
  service_name = module.service.name

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity

  scale_down_adjustment = var.scale_down_adjustment
  scale_up_adjustment   = var.scale_up_adjustment
}

module "task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition?ref=v3.12.2"

  cpu    = var.host_cpu
  memory = var.host_memory

  launch_types = [var.launch_type]
  task_name    = "${var.namespace}_${var.name}"

  container_definitions = concat(local.app_container_definitions, local.other_container_definitions)
  volumes               = var.volumes
}

module "app_container" {
  source   = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.12.2"
  for_each = var.apps

  name  = each.key
  image = each.value.image

  cpu    = each.value.cpu
  memory = each.value.memory

  environment = each.value.env_vars
  secrets     = each.value.secret_env_vars

  healthcheck = each.value.healthcheck

  log_configuration = module.log_router_container.container_log_configuration

  mount_points = each.value.mount_points
}

module "app_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.12.2"
  secrets   = local.all_secret_env_vars
  role_name = module.task_definition.task_execution_role_name
}

module "sidecar_container" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.12.2"

  name  = var.manager_container_name
  image = var.manager_container_image

  cpu    = var.manager_cpu
  memory = var.manager_memory

  environment = merge(
    {
      metrics_namespace = "${var.namespace}_${var.name}",
      queue_url         = module.input_queue.url,
    },
    var.manager_env_vars,
  )
  secrets     = var.manager_secret_env_vars

  depends = [for name, app in var.apps : {
    containerName = name
    condition     = "HEALTHY"
  } if app.healthcheck != null]

  log_configuration = module.log_router_container.container_log_configuration

  mount_points = var.manager_mount_points
}

module "sidecar_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.12.2"
  secrets   = var.manager_secret_env_vars
  role_name = module.task_definition.task_execution_role_name
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens?ref=v3.12.2"
  namespace = "${var.namespace}_${var.name}"

  use_privatelink_endpoint = true
}

module "log_router_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets?ref=v3.12.2"
  secrets   = var.shared_logging_secrets
  role_name = module.task_definition.task_execution_role_name
}
