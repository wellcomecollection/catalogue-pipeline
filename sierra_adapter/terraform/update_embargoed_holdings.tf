module "update_embargoed_holdings" {
  source = "./update_embargoed_holdings"

  topic_arn            = module.holdings_window_generator.topic_arn
  topic_publish_policy = module.holdings_window_generator.topic_publish_policy

  lambda_error_alarm_arn = local.lambda_error_alarm_arn
  infra_bucket           = var.infra_bucket
}
