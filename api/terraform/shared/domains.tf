resource "aws_acm_certificate" "catalogue_api" {
  domain_name               = local.prod_domain_name
  subject_alternative_names = [local.staging_domain_name]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

locals {
  validation_opts = aws_acm_certificate.catalogue_api.domain_validation_options
}

data "aws_route53_zone" "dotorg" {
  provider = aws.dns
  name = "wellcomecollection.org."
}

resource "aws_route53_record" "cert_validation" {
  provider = aws.dns

  count   = length(local.validation_opts)
  name    = lookup(local.validation_opts[count.index], "resource_record_name")
  type    = lookup(local.validation_opts[count.index], "resource_record_type")
  zone_id = data.aws_route53_zone.dotorg.id
  records = [lookup(local.validation_opts[count.index], "resource_record_value")]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "catalogue_api_validation" {
  certificate_arn         = aws_acm_certificate.catalogue_api.arn
  validation_record_fqdns = aws_route53_record.cert_validation.*.fqdn
}
