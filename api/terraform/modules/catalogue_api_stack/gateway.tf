resource "aws_api_gateway_base_path_mapping" "catalogue_api" {
  api_id      = "${var.api_gateway_id}"
  stage_name  = "${var.environment}"
  domain_name = "${aws_api_gateway_domain_name.catalogue_api.domain_name}"
  base_path   = "catalogue"
}
