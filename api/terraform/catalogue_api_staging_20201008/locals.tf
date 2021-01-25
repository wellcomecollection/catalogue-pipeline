locals {
  environment = "staging"
  namespace   = "catalogue_api"

  vpc_id          = local.catalogue_vpcs["catalogue_vpc_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_private_subnets"]

  cluster_arn = data.terraform_remote_state.catalogue_api_shared.outputs.cluster_arn
  nlb_arn     = data.terraform_remote_state.catalogue_api_shared.outputs.nlb_arn

  api_repository_url  = data.terraform_remote_state.catalogue_api_shared.outputs.ecr_api_repository_url
  api_container_image = "${local.api_repository_url}:env.${local.environment}"

  egress_security_group_id             = data.terraform_remote_state.catalogue_api_shared.outputs.egress_security_group_id
  interservice_security_group_id       = data.terraform_remote_state.catalogue_api_shared.outputs.interservice_security_group_id
  service_lb_ingress_security_group_id = data.terraform_remote_state.catalogue_api_shared.outputs.service_lb_ingress_security_group_id
  elastic_cloud_vpce_sg_id             = data.terraform_remote_state.infra_critical.outputs["catalogue_elastic_cloud_sg_id"]

  service_discovery_namespace_id = data.terraform_remote_state.catalogue_api_shared.outputs.prod_namespace
}
