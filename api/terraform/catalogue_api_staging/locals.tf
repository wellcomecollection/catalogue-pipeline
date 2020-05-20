locals {
  namespace        = "catalogue_api"
  namespace_hyphen = replace(local.namespace, "_", "-")

  vpc_id          = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_id
  private_subnets = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_private_subnets

  logstash_transit_service_name = data.terraform_remote_state.catalogue_api_shared.outputs.logstash_transit_service_name
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace_hyphen}"

  api_gateway_id  = data.terraform_remote_state.catalogue_api_shared.outputs.api_gateway_id
  certificate_arn = data.terraform_remote_state.catalogue_api_shared.outputs.certificate_arn
  cluster_arn     = data.terraform_remote_state.catalogue_api_shared.outputs.cluster_arn
  nlb_arn         = data.terraform_remote_state.catalogue_api_shared.outputs.nlb_arn

  egress_security_group_id             = data.terraform_remote_state.catalogue_api_shared.outputs.egress_security_group_id
  interservice_security_group_id       = data.terraform_remote_state.catalogue_api_shared.outputs.interservice_security_group_id
  service_lb_ingress_security_group_id = data.terraform_remote_state.catalogue_api_shared.outputs.service_lb_ingress_security_group_id

  service_discovery_namespace_id = data.terraform_remote_state.catalogue_api_shared.outputs.staging_namespace
}
