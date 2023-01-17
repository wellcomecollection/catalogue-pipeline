locals {
  vpc_id_new          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets_new = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

  admin_cidr_ingress = data.aws_ssm_parameter.admin_cidr_ingress.value

  read_principles = [
    "arn:aws:iam::269807742353:root",
    "arn:aws:iam::964279923020:role/datascience_ec2",
    "arn:aws:iam::964279923020:root",
  ]
}

data "aws_ssm_parameter" "admin_cidr_ingress" {
  name = "/infra_critical/config/prod/admin_cidr_ingress"
}
