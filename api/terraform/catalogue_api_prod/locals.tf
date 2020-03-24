locals {
  namespace        = "catalogue_api"
  namespace_hyphen = "${replace(local.namespace,"_","-")}"

  vpc_id                         = "${data.terraform_remote_state.shared_infra.catalogue_vpc_id}"
  private_subnets                = "${data.terraform_remote_state.shared_infra.catalogue_vpc_private_subnets}"
  gateway_server_error_alarm_arn = "${data.terraform_remote_state.shared_infra.gateway_server_error_alarm_arn}"

  # This is the Zone ID for wellcomecollection.org in the routemaster account.
  # We can't look this up programatically because the role we use doesn't have
  # the right permissions in that account.
  route53_zone_id = "Z3THRVQ5VDYDMC"

  logstash_transit_service_name = "${data.terraform_remote_state.catalogue_api_shared.logstash_transit_service_name}"
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace_hyphen}"

  api_gateway_id   = "${data.terraform_remote_state.catalogue_api_shared.api_gateway_id}"
  api_gateway_name = "${data.terraform_remote_state.catalogue_api_shared.api_gateway_name}"
  certificate_arn  = "${data.terraform_remote_state.catalogue_api_shared.certificate_arn}"
  cluster_name     = "${data.terraform_remote_state.catalogue_api_shared.cluster_name}"
  nlb_arn          = "${data.terraform_remote_state.catalogue_api_shared.nlb_arn}"

  interservice_security_group_id       = "${data.terraform_remote_state.catalogue_api_shared.interservice_security_group_id}"
  service_lb_ingress_security_group_id = "${data.terraform_remote_state.catalogue_api_shared.service_lb_ingress_security_group_id}"
}
