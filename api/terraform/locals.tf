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

  platform_developer_role_arn  = "arn:aws:iam::760097843905:role/platform-developer"
  catalogue_developer_role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"

  service_repositories = [
    "api",
    "nginx_api-gw",
    "snapshot_generator",
    "update_api_docs",
  ]

  prod_domain_name    = "catalogue.api.wellcomecollection.org"
  staging_domain_name = "catalogue.api-stage.wellcomecollection.org"

  logstash_transit_service_name = "${local.namespace_hyphen}_logstash_transit"
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace_hyphen}"
}
