locals {
  namespace        = "catalogue_api"
  namespace_hyphen = replace(local.namespace, "_", "-")

  vpc_id          = local.catalogue_vpcs["catalogue_vpc_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_private_subnets"]

  prod_domain_name    = "catalogue.api.wellcomecollection.org"
  staging_domain_name = "catalogue.api-stage.wellcomecollection.org"
}
