module "sierra_to_dynamo_service" {
  source = "../../../infrastructure/modules/worker"

  name = local.service_name

  image = var.container_image

  env_vars = {
    demultiplexer_queue_url = module.demultiplexer_queue.url
    metrics_namespace = local.service_name

    vhs_table_name  = var.vhs_sierra_items_table_name
    vhs_bucket_name = var.vhs_sierra_items_bucket_name

    topic_arn = module.sierra_to_dynamo_updates_topic.arn
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
