data "aws_ecs_cluster" "cluster" {
  cluster_name = "${var.cluster_name}"
}

module "sierra_merger_service" {
  source = "../modules/scaling_worker"

  service_name = "sierra_item_merger"

  container_image = var.container_image

  env_vars = {
    windows_queue_url   = "${module.updates_queue.id}"
    metrics_namespace   = "sierra_item_merger"
    dynamo_table_name   = "${var.merged_dynamo_table_name}"
    bucket_name         = "${var.bucket_name}"
    sierra_items_bucket = "${var.sierra_items_bucket}"
    topic_arn           = "${module.sierra_item_merger_results.arn}"

    # The item merger has to write lots of S3 objects, and we've seen issues
    # where we exhaust the HTTP connection pool.  Turning down the parallelism
    # is an attempt to reduce the number of S3 objects in flight, and avoid
    # these errors.
    sqs_parallelism = 5
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
