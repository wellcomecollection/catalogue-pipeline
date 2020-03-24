locals {
  namespace        = "catalogue_api"
  namespace_hyphen = "${replace(local.namespace,"_","-")}"
  prod_name        = "prod"
  staging_name     = "staging"

  prod_task_number    = 3
  staging_task_number = 1

  prod_listener_port    = "80"
  staging_listener_port = "8080"

  vpc_id                         = "${data.terraform_remote_state.shared_infra.catalogue_vpc_id}"
  private_subnets                = "${data.terraform_remote_state.shared_infra.catalogue_vpc_private_subnets}"
  gateway_server_error_alarm_arn = "${data.terraform_remote_state.shared_infra.gateway_server_error_alarm_arn}"

  // This is taken from the routemaster AWS account which doesn't expose its terraform state
  routemaster_router53_zone_id = "Z3THRVQ5VDYDMC"

  service_repositories = [
    "api",
    "nginx_api-gw",
  ]

  prod_domain_name    = "catalogue.api.wellcomecollection.org"
  staging_domain_name = "catalogue.api-stage.wellcomecollection.org"

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
