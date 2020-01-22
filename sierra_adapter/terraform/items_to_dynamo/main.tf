data "aws_ecs_cluster" "cluster" {
  cluster_name = "${var.cluster_name}"
}

module "sierra_to_dynamo_service" {
  source = "../modules/scaling_worker"

  service_name = "sierra_items_to_dynamo"

  container_image = var.container_image

  env_vars = {
    demultiplexer_queue_url = module.demultiplexer_queue.id
    metrics_namespace       = "sierra_items_to_dynamo"

    vhs_table_name  = var.vhs_sierra_items_table_name
    vhs_bucket_name = var.vhs_sierra_items_bucket_name

    topic_arn = module.sierra_to_dynamo_updates_topic.arn
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
