module "sierra-adapter-20200604" {
  source = "./stack"

  namespace     = "sierra-adapter-20200604"
  release_label = "latest"

  dlq_alarm_arn                  = local.dlq_alarm_arn
  egress_security_group_id       = aws_security_group.egress_security_group.id
  infra_bucket                   = var.infra_bucket
  interservice_security_group_id = aws_security_group.interservice_security_group.id
  lambda_error_alarm_arn         = local.lambda_error_alarm_arn

  private_subnets          = local.private_subnets
  vpc_id                   = local.vpc_id
  bibs_windows_topic_arns  = [module.bibs_window_generator.topic_arn, module.bibs_reharvest_topic.arn]
  items_windows_topic_arns = [module.items_window_generator.topic_arn, module.items_reharvest_topic.arn]
}
