module "catalogue_api_staging" {
  source = "../modules/stack"

  environment        = "staging"
  listener_port      = 1234
  desired_task_count = 1

  namespace   = local.namespace
  vpc_id      = local.vpc_id
  subnets     = local.private_subnets
  cluster_arn = local.cluster_arn

  service_discovery_namespace_id = local.service_discovery_namespace_id

  lb_arn           = local.nlb_arn
  lb_ingress_sg_id = local.service_lb_ingress_security_group_id

  egress_security_group_id = local.egress_security_group_id

  logstash_host = local.logstash_host

  interservice_sg_id = local.interservice_security_group_id

  providers = {
    aws.platform = aws.platform
  }
}
