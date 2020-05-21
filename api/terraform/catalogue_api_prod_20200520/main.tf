module "catalogue_api_prod_20200520" {
  source = "../modules/new_catalogue_api_stack"

  environment        = "prod"
  instance           = "20200520"
  listener_port      = 8080
  desired_task_count = 3

  namespace   = local.namespace
  vpc_id      = local.vpc_id
  subnets     = local.private_subnets
  cluster_arn = local.cluster_arn

  lb_arn           = local.nlb_arn
  lb_ingress_sg_id = local.service_lb_ingress_security_group_id

  egress_security_group_id = local.egress_security_group_id

  logstash_host = local.logstash_host

  interservice_sg_id = local.interservice_security_group_id

  service_discovery_namespace_id = local.service_discovery_namespace_id

  providers = {
    aws.platform    = aws.platform
    aws.routemaster = aws.routemaster
  }
}
