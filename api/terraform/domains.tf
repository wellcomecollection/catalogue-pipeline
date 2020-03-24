resource "aws_api_gateway_domain_name" "prod" {
  domain_name              = "${local.prod_domain_name}"
  regional_certificate_arn = "${local.certificate_arn}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_domain_name" "staging" {
  domain_name              = "${local.staging_domain_name}"
  regional_certificate_arn = "${local.certificate_arn}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_route53_record" "prod" {
  provider = "aws.routemaster"
  zone_id  = "${local.routemaster_router53_zone_id}"
  name     = "${aws_api_gateway_domain_name.prod.domain_name}"
  type     = "A"

  alias {
    name                   = "${aws_api_gateway_domain_name.prod.regional_domain_name}"
    zone_id                = "${aws_api_gateway_domain_name.prod.regional_zone_id}"
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "staging" {
  provider = "aws.routemaster"
  zone_id  = "${local.routemaster_router53_zone_id}"
  name     = "${aws_api_gateway_domain_name.staging.domain_name}"
  type     = "A"

  alias {
    name                   = "${aws_api_gateway_domain_name.staging.regional_domain_name}"
    zone_id                = "${aws_api_gateway_domain_name.staging.regional_zone_id}"
    evaluate_target_health = false
  }
}
