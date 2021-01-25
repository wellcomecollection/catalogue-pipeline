data "aws_ssm_parameter" "admin_cidr_ingress" {
  name = "/infra_critical/config/prod/admin_cidr_ingress"
}

locals {
  vpc_id_new          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  public_subnets_new  = local.catalogue_vpcs["catalogue_vpc_delta_public_subnets"]
  private_subnets_new = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

  catalogue_pipeline_ec_vpce_domain      = "vpce.eu-west-1.aws.elastic-cloud.com"

  # The correct endpoints are provided by Elastic Cloud
  # https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
  ec_eu_west_1_service_name = "com.amazonaws.vpce.eu-west-1.vpce-svc-01f2afe87944eb12b"

  admin_cidr_ingress = data.aws_ssm_parameter.admin_cidr_ingress.value

  read_principles = [
    "arn:aws:iam::269807742353:root",
    "arn:aws:iam::964279923020:role/datascience_ec2",
    "arn:aws:iam::964279923020:root",
  ]
}
