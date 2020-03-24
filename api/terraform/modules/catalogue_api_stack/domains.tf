data "aws_route53_zone" "dotorg" {
  provider = "aws.routemaster"

  name = "wellcomecollection.org."
}

resource "aws_api_gateway_domain_name" "catalogue_api" {
  domain_name              = "${var.domain_name}"
  regional_certificate_arn = "${var.certificate_arn}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_route53_record" "catalogue_api" {
  provider = "aws.routemaster"
  zone_id  = "${data.aws_route53_zone.dotorg.id}"
  name     = "${aws_api_gateway_domain_name.catalogue_api.domain_name}"
  type     = "A"

  alias {
    name                   = "${aws_api_gateway_domain_name.catalogue_api.regional_domain_name}"
    zone_id                = "${aws_api_gateway_domain_name.catalogue_api.regional_zone_id}"
    evaluate_target_health = false
  }
}
