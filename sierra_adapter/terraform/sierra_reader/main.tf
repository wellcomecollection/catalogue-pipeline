data "aws_ecs_cluster" "cluster" {
  cluster_name = var.cluster_name
}

module "sierra_reader_service" {
  source = "../modules/scaling_worker"

  service_name = local.service_name

  container_image = var.container_image

  env_vars = {
    resource_type     = var.resource_type
    windows_queue_url = module.windows_queue.url
    bucket_name       = var.bucket_name
    metrics_namespace = local.service_name
    sierra_api_url    = var.sierra_api_url
    sierra_fields     = var.sierra_fields
    batch_size        = 50
  }

  secret_env_vars = {
    sierra_oauth_secret = "sierra_adapter/sierra_api_client_secret"
    sierra_oauth_key    = "sierra_adapter/sierra_api_key"
  }

  cpu    = 256
  memory = 512

  min_capacity = 0
  max_capacity = 3

  namespace_id = var.namespace_id

  cluster_name = var.cluster_name
  cluster_arn  = data.aws_ecs_cluster.cluster.id

  subnets = var.subnets

  security_group_ids = [
    var.interservice_security_group_id,
    var.service_egress_security_group_id,
  ]
}
