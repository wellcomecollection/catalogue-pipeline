module "snapshot_generator" {
  source = "./snapshot_generator"

  input_queue_name = module.snapshot_generator_queue.name
  input_queue_url  = module.snapshot_generator_queue.url
  output_topic_arn = module.snapshot_complete_topic.arn

  cluster_arn  = aws_ecs_cluster.cluster.arn
  cluster_name = aws_ecs_cluster.cluster.name

  subnets = local.private_subnets

  namespace_id = local.service_discovery_namespace

  security_group_ids = [
    aws_security_group.egress_security_group.id,
  ]

  aws_region = var.aws_region

  providers = {
    aws.platform = aws.platform
  }
}

module "snapshot_scheduler" {
  source = "./snapshot_scheduler"

  lambda_error_alarm_arn = "${local.lambda_error_alarm_arn}"
  infra_bucket           = "${local.infra_bucket}"

  public_bucket_name = "${aws_s3_bucket.public_data.id}"

  public_object_key_v2 = "catalogue/v2/works.json.gz"
}
