locals {
  namespace        = "catalogue_api"
  namespace_hyphen = "${replace(local.namespace, "_", "-")}"

  vpc_id          = "${data.terraform_remote_state.shared_infra.catalogue_vpc_id}"
  private_subnets = "${data.terraform_remote_state.shared_infra.catalogue_vpc_private_subnets}"

  # This is the Zone ID for wellcomecollection.org in the routemaster account.
  # We can't look this up programatically because the role we use doesn't have
  # the right permissions in that account.
  route53_zone_id = "Z3THRVQ5VDYDMC"

  service_repositories = [
    "api",
    "nginx_api-gw",
  ]

  prod_domain_name    = "catalogue.api.wellcomecollection.org"
  staging_domain_name = "catalogue.api-stage.wellcomecollection.org"

  logstash_transit_service_name = "${local.namespace_hyphen}_logstash_transit"
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace_hyphen}"
}
