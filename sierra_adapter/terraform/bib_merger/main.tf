
module "sierra_merger_service" {
  source = "../../../infrastructure/modules/worker"

  name = local.service_name

  image = var.container_image

  env_vars = {
    windows_queue_url = module.updates_queue.url
    metrics_namespace = local.service_name
    dynamo_table_name = var.merged_dynamo_table_name
    bucket_name       = var.bucket_name
    topic_arn         = module.sierra_bib_merger_results.arn
  }

  min_capacity = 0
  max_capacity = 3

  use_fargate_spot = true

  namespace_id = var.namespace_id

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  subnets = var.subnets

  security_group_ids = [
    var.interservice_security_group_id,
    var.service_egress_security_group_id,
  ]
}
