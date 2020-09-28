module "snapshot_generator" {
  source     = "snapshot_generator"
  aws_region = var.aws_region

  cluster_arn  = var.cluster_arn
  cluster_name = var.cluster_name

  snapshot_generator_image = var.snapshot_generator_image
  deployment_service_env   = var.deployment_service_env

  snapshot_generator_input_topic_arn = ""

  dlq_alarm_arn = var.dlq_alarm_arn

  vpc_id  = var.vpc_id
  subnets = var.subnets
}

module "snapshot_scheduler" {
  source = "snapshot_scheduler"

  deployment_service_env = var.deployment_service_env

  infra_bucket = var.infra_bucket

  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  public_bucket_name   = var.public_bucket_name
  public_object_key_v2 = var.public_object_key_v2
}
