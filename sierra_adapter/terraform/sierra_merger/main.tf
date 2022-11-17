module "service" {
  source = "../../../infrastructure/modules/worker"

  name = local.service_name

  image = var.container_image

  env_vars = {
    sierra_vhs_dynamo_table_name = var.vhs_table_name
    sierra_vhs_bucket_name       = var.vhs_bucket_name

    windows_queue_url = module.input_queue.url
    topic_arn         = module.output_topic.arn

    metrics_namespace = local.service_name

    resource_type = var.resource_type
  }

  min_capacity = 0
  max_capacity = 3

  use_fargate_spot = true

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
}
