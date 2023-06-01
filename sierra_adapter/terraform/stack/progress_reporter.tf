module "progress_reporter" {
  source = "./../sierra_progress_reporter"

  trigger_interval_minutes = 240
  s3_adapter_bucket_name   = aws_s3_bucket.sierra_adapter.id

  infra_bucket = var.infra_bucket

  lambda_error_alarm_arn = var.lambda_error_alarm_arn

  namespace = local.namespace_hyphen
}
