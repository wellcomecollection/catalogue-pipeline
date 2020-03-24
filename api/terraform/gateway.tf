# Base path mappings
resource "aws_api_gateway_base_path_mapping" "catalogue_prod" {
  api_id      = "${local.api_gateway_id}"
  stage_name  = "${local.prod_name}"
  domain_name = "${aws_api_gateway_domain_name.prod.domain_name}"
  base_path   = "catalogue"
}

resource "aws_api_gateway_base_path_mapping" "catalogue_staging" {
  api_id      = "${local.api_gateway_id}"
  stage_name  = "${local.staging_name}"
  domain_name = "${aws_api_gateway_domain_name.staging.domain_name}"
  base_path   = "catalogue"
}
