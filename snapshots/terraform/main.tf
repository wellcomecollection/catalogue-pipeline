module "stack" {
  source = "stack"

  aws_region = var.aws_region

  cluster_arn  = local.cluster_arn
  cluster_name = local.cluster_name

  snapshot_generator_image = local.snapshot_generator_image
  deployment_service_env   = "prod"

  public_bucket_name   = local.public_data_bucket_name
  public_object_key_v2 = local.public_object_key_v2

  dlq_alarm_arn          = local.dlq_alarm_arn
  lambda_error_alarm_arn = local.lambda_error_alarm_arn

  vpc_id  = local.vpc_id
  subnets = local.subnets

  infra_bucket = local.infra_bucket
}
