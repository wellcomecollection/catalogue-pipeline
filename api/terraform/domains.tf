resource "aws_acm_certificate" "catalogue_api" {
  domain_name       = "catalogue.api.wellcomecollection.org"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert_validation" {
  provider = "aws.routermaster"
  name     = "${aws_acm_certificate.catalogue_api.domain_validation_options.0.resource_record_name}"
  type     = "${aws_acm_certificate.catalogue_api.domain_validation_options.0.resource_record_type}"
  zone_id  = "${local.routermaster_router53_zone_id}"
  records  = ["${aws_acm_certificate.catalogue_api.domain_validation_options.0.resource_record_value}"]
  ttl      = 60
}

resource "aws_acm_certificate_validation" "catalogue_api_validation" {
  certificate_arn         = "${aws_acm_certificate.catalogue_api.arn}"
  validation_record_fqdns = ["${aws_route53_record.cert_validation.fqdn}"]
}

module "catalogue_api_domain_prod" {
  source = "git::https://github.com/wellcometrust/terraform.git//api_gateway/modules/domain?ref=v18.2.3"

  domain_name      = "catalogue.api.wellcomecollection.org"
  cert_domain_name = "${aws_acm_certificate.catalogue_api.domain_name}"
}

module "catalogue_api_domain_stage" {
  source = "git::https://github.com/wellcometrust/terraform.git//api_gateway/modules/domain?ref=v18.2.3"

  domain_name      = "catalogue.api-stage.wellcomecollection.org"
  cert_domain_name = "${aws_acm_certificate.catalogue_api.domain_name}"
}

provider "aws" {
  region  = "eu-west-1"
  version = "1.57.0"
  alias   = "routermaster"

  assume_role {
    role_arn = "arn:aws:iam::250790015188:role/wellcomecollection-assume_role_hosted_zone_update"
  }
}
