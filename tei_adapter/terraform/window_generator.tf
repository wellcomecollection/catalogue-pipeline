module "tei_window_generator_lambda" {
  source = "./modules/window_generator"

  window_length_minutes    = 50
  trigger_interval_minutes = 30

  lambda_error_alarm_arn = local.lambda_error_alarm_arn
  infra_bucket           = local.infra_bucket
}

