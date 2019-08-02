data "aws_ssm_parameter" "admin_cidr_ingress" {
  name = "/infra_critical/config/prod/admin_cidr_ingress"
}

locals {
  vpc_id_new          = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_id}"
  public_subnets_new  = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_public_subnets}"
  private_subnets_new = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_private_subnets}"

  admin_cidr_ingress = "${data.aws_ssm_parameter.admin_cidr_ingress.value}"
}
