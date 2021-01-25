# This enables public access to the ES cluster (with the usual x-pack auth proviso)
# TODO: Restrict this to Wellcome internal IP addresses when physical office access is restored
resource "ec_deployment_traffic_filter" "public_internet" {
  provider = ec

  name   = "public_access"
  region = "eu-west-1"
  type   = "ip"

  rule {
    source = "0.0.0.0/0"
  }
}

module "platform_privatelink" {
  source = "./modules/elasticsearch_privatelink"

  vpc_id              = local.vpc_id_new
  service_name        = local.ec_eu_west_1_service_name
  subnet_ids          = local.private_subnets_new
  ec_vpce_domain      = local.catalogue_pipeline_ec_vpce_domain
  traffic_filter_name = "ec_allow_vpc_endpoint"
}

