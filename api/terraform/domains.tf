resource "aws_acm_certificate" "catalogue_api" {
  domain_name = local.prod_domain_name
  # TF-UPGRADE-TODO: In Terraform v0.10 and earlier, it was sometimes necessary to
  # force an interpolation expression to be interpreted as a list by wrapping it
  # in an extra set of list brackets. That form was supported for compatibility in
  # v0.11, but is no longer supported in Terraform v0.12.
  #
  # If the expression in the following list itself returns a list, remove the
  # brackets to avoid interpretation as a list of lists. If the expression
  # returns a single list item then leave it as-is and remove this TODO comment.
  subject_alternative_names = [local.staging_domain_name]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

locals {
  validation_opts = aws_acm_certificate.catalogue_api.domain_validation_options
}

resource "aws_route53_record" "cert_validation" {
  count    = length(local.validation_opts)
  provider = aws.routemaster
  name     = local.validation_opts[count.index]["resource_record_name"]
  type     = local.validation_opts[count.index]["resource_record_type"]
  zone_id  = local.routemaster_router53_zone_id
  # TF-UPGRADE-TODO: In Terraform v0.10 and earlier, it was sometimes necessary to
  # force an interpolation expression to be interpreted as a list by wrapping it
  # in an extra set of list brackets. That form was supported for compatibility in
  # v0.11, but is no longer supported in Terraform v0.12.
  #
  # If the expression in the following list itself returns a list, remove the
  # brackets to avoid interpretation as a list of lists. If the expression
  # returns a single list item then leave it as-is and remove this TODO comment.
  records = [local.validation_opts[count.index]["resource_record_value"]]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "catalogue_api_validation" {
  certificate_arn         = aws_acm_certificate.catalogue_api.arn
  validation_record_fqdns = aws_route53_record.cert_validation.*.fqdn
}

resource "aws_api_gateway_domain_name" "prod" {
  domain_name              = local.prod_domain_name
  regional_certificate_arn = aws_acm_certificate_validation.catalogue_api_validation.certificate_arn

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_domain_name" "staging" {
  domain_name              = local.staging_domain_name
  regional_certificate_arn = aws_acm_certificate_validation.catalogue_api_validation.certificate_arn

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_route53_record" "prod" {
  provider = aws.routemaster
  zone_id  = local.routemaster_router53_zone_id
  name     = aws_api_gateway_domain_name.prod.domain_name
  type     = "A"

  alias {
    name                   = aws_api_gateway_domain_name.prod.regional_domain_name
    zone_id                = aws_api_gateway_domain_name.prod.regional_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "staging" {
  provider = aws.routemaster
  zone_id  = local.routemaster_router53_zone_id
  name     = aws_api_gateway_domain_name.staging.domain_name
  type     = "A"

  alias {
    name                   = aws_api_gateway_domain_name.staging.regional_domain_name
    zone_id                = aws_api_gateway_domain_name.staging.regional_zone_id
    evaluate_target_health = false
  }
}

