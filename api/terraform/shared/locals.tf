locals {
  namespace        = "catalogue_api"
  namespace_hyphen = "${replace(local.namespace, "_", "-")}"

  vpc_id          = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_id
  private_subnets = data.terraform_remote_state.shared_infra.outputs.catalogue_vpc_private_subnets

  prod_domain_name    = "catalogue.api.wellcomecollection.org"
  staging_domain_name = "catalogue.api-stage.wellcomecollection.org"
}
