module "stack" {
  source = "./stack"

  aws_region = var.aws_region

  cluster_arn  = local.cluster_arn
  cluster_name = local.cluster_name

  snapshot_generator_image = local.snapshot_generator_image
  deployment_service_env   = "prod"

  public_bucket_name   = local.public_data_bucket_name
  public_object_key_v2 = local.public_object_key_v2

  # How many documents to fetch in a single scroll request?  We choose this such that
  #
  #     #(number of works in request) × (size of a work) < Elasticsearch heap size
  #
  # otherwise a single scroll request blows the Elasticsearch heap, and snapshot
  # generation fails.
  #
  # In general, the size of a work grows over time, so if we start hitting exceptions
  # like 'ContentTooLongException', we should consider turning down this number.
  # It used to be set to 1000, but that became too big in November 2020.
  #
  # See https://github.com/wellcomecollection/platform/issues/4901
  es_bulk_size = 500

  shared_logging_secrets = local.shared_logging_secrets

  dlq_alarm_arn          = local.dlq_alarm_arn
  lambda_error_alarm_arn = local.lambda_error_alarm_arn

  vpc_id  = local.vpc_id
  subnets = local.subnets

  lambda_upload_bucket = "wellcomecollection-catalogue-infra-delta"
}
