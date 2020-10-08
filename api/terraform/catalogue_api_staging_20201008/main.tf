module "catalogue_api_staging_20201008" {
  source = "../modules/stack"

  environment        = local.environment
  instance           = "20201008"
  listener_port      = 1236
  desired_task_count = 1

  namespace   = local.namespace
  vpc_id      = local.vpc_id
  subnets     = local.private_subnets
  cluster_arn = local.cluster_arn

  lb_arn           = local.nlb_arn
  lb_ingress_sg_id = local.service_lb_ingress_security_group_id

  egress_security_group_id = local.egress_security_group_id

  interservice_sg_id = local.interservice_security_group_id

  service_discovery_namespace_id = local.service_discovery_namespace_id

  api_container_image = local.api_container_image

  providers = {
    aws.platform = aws.platform
  }
}
