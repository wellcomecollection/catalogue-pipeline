module "sierra-adapter-20200529" {
  source = "./stack"

  namespace = "sierra-adapter-20200529"
  release_label = "latest"

  dlq_alarm_arn = local.dlq_alarm_arn
  egress_security_group_id = aws_security_group.egress_security_group.id
  infra_bucket = var.infra_bucket
  interservice_security_group_id = aws_security_group.interservice_security_group.id
  lambda_error_alarm_arn = local.lambda_error_alarm_arn

  private_subnets = local.private_subnets
  vpc_id = local.vpc_id
}
