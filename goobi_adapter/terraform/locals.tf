locals {
  vpc_id          = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_id}"
  private_subnets = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_private_subnets}"

  vhs_goobi_full_access_policy = "${data.terraform_remote_state.catalogue_infra_critical.vhs_goobi_full_access_policy}"
  vhs_goobi_table_name         = "${data.terraform_remote_state.catalogue_infra_critical.vhs_goobi_table_name}"
  vhs_goobi_bucket_name        = "${data.terraform_remote_state.catalogue_infra_critical.vhs_goobi_bucket_name}"
}
