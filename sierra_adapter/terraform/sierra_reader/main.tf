module "sierra_reader_service" {
  source = "../../../infrastructure/modules/worker"

  name = local.service_name

  deployment_service_env  = var.deployment_service_env
  deployment_service_name = var.deployment_service_name

  image = var.container_image

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

  min_capacity = 0
  max_capacity = 3

  namespace_id = var.namespace_id

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  subnets = var.subnets

  security_group_ids = [
    # TODO: Do we need this interservice security group?
    var.interservice_security_group_id,
    var.service_egress_security_group_id,
  ]
  elastic_cloud_vpce_sg_id = var.elastic_cloud_vpce_sg_id

  shared_logging_secrets = var.shared_logging_secrets

  use_fargate_spot = true
}
