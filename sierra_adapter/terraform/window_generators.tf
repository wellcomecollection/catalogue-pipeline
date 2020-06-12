module "bibs_window_generator" {
  source = "./sierra_window_generator"

  resource_type = "bibs"

  window_length_minutes    = 16
  trigger_interval_minutes = 7

  lambda_error_alarm_arn = local.lambda_error_alarm_arn
  infra_bucket           = var.infra_bucket
}

module "items_window_generator" {
  source = "./sierra_window_generator"

  resource_type = "items"

  window_length_minutes    = 31
  trigger_interval_minutes = 15

  lambda_error_alarm_arn = local.lambda_error_alarm_arn
  infra_bucket           = var.infra_bucket
}
