/*data "aws_route53_zone" "dotorg" {
  provider = aws.routemaster

  name = "wellcomecollection.org."
}*/

locals {
  # This is the Zone ID for wellcomecollection.org in the routemaster account.
  # We can't look this up programatically because the role we use doesn't have
  # the right permissions in that account.
  route53_zone_id = "Z3THRVQ5VDYDMC"
}

resource "aws_api_gateway_domain_name" "catalogue_api" {
  domain_name              = "${var.domain_name}"
  regional_certificate_arn = "${var.certificate_arn}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_route53_record" "catalogue_api" {
  provider = aws.routemaster

  zone_id = local.route53_zone_id
  name    = "${aws_api_gateway_domain_name.catalogue_api.domain_name}"
  type    = "A"

  alias {
    name                   = "${aws_api_gateway_domain_name.catalogue_api.regional_domain_name}"
    zone_id                = "${aws_api_gateway_domain_name.catalogue_api.regional_zone_id}"
    evaluate_target_health = false
  }
}
