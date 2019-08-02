data "aws_ssm_parameter" "admin_cidr_ingress" {
  name = "/infra_critical/config/prod/admin_cidr_ingress"
}

locals {
  vpc_id_new          = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_id}"
  public_subnets_new  = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_public_subnets}"
  private_subnets_new = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_private_subnets}"

  admin_cidr_ingress = "${data.aws_ssm_parameter.admin_cidr_ingress.value}"

  read_principles = [
    # Reporting
    "arn:aws:iam::269807742353:root",

    # Data science
    "arn:aws:iam::964279923020:role/datascience_ec2",
    "arn:aws:iam::964279923020:root",
  ]
}
