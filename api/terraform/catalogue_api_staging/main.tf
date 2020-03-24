module "catalogue_api_staging" {
  source = "../modules/catalogue_api_stack"

  environment        = "staging"
  domain_name        = "catalogue.api-stage.wellcomecollection.org"
  listener_port      = 8080
  desired_task_count = 1

  namespace   = local.namespace
  vpc_id      = local.vpc_id
  subnets     = local.private_subnets
  cluster_arn = local.cluster_arn
  api_id      = local.api_gateway_id

  lb_arn           = local.nlb_arn
  lb_ingress_sg_id = local.service_lb_ingress_security_group_id

  logstash_host = local.logstash_host

  interservice_sg_id = local.interservice_security_group_id

  certificate_arn = local.certificate_arn

  api_gateway_id = local.api_gateway_id

  providers = {
    aws.platform    = aws.platform
    aws.routemaster = aws.routemaster
  }
}
