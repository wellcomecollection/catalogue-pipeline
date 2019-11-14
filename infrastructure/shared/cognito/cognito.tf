# Route 53
provider "aws" {
  region  = "eu-west-1"
  alias   = "routemaster"
  version = "~> 2.35"

  assume_role {
    role_arn = "arn:aws:iam::250790015188:role/wellcomecollection-assume_role_hosted_zone_update"
  }
}

provider "aws" {
  region  = "us-east-1"
  alias   = "aws_us_1"
  version = "~> 2.35"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

resource "aws_acm_certificate" "id" {
  domain_name       = "id.wellcomecollection.org"
  validation_method = "DNS"
  provider          = "aws.aws_us_1"
}

data "aws_route53_zone" "weco_zone" {
  name         = "wellcomecollection.org."
  private_zone = false
  provider     = "aws.routemaster"
}

resource "aws_route53_record" "cert_validation" {
  name     = aws_acm_certificate.id.domain_validation_options[0].resource_record_name
  type     = aws_acm_certificate.id.domain_validation_options[0].resource_record_type
  zone_id  = data.aws_route53_zone.weco_zone.id
  records  = [aws_acm_certificate.id.domain_validation_options[0].resource_record_value]
  ttl      = 60
  provider = "aws.routemaster"
}

resource "aws_acm_certificate_validation" "id_cert" {
  certificate_arn         = aws_acm_certificate.id.arn
  validation_record_fqdns = [aws_route53_record.cert_validation.fqdn]
  provider                = "aws.aws_us_1"
}

resource "aws_route53_record" "cognito_cloudfront_distribution" {
  name    = "id.wellcomecollection.org"
  type    = "A"
  zone_id = data.aws_route53_zone.weco_zone.id

  alias {
    name                   = aws_cognito_user_pool_domain.id.cloudfront_distribution_arn
    zone_id                = "Z2FDTNDATAQYW2"
    evaluate_target_health = true
  }
  provider = "aws.routemaster"
}

# Cognito
resource "aws_cognito_user_pool" "pool" {
  name = "Wellcome Collection Identity"

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  password_policy {
    minimum_length = 8
  }
}

resource "aws_cognito_user_pool_domain" "id" {
  domain          = "id.wellcomecollection.org"
  certificate_arn = aws_acm_certificate.id.arn
  user_pool_id    = aws_cognito_user_pool.pool.id
}

resource "aws_cognito_resource_server" "stacks_api" {
  identifier = "https://api.wellcomecollection.org/stacks/v1"
  name       = "Stacks API V1"

  scope {
    scope_name        = "requests_readwrite"
    scope_description = "Read and write requests"
  }

  scope {
    scope_name        = "items_readonly"
    scope_description = "Read the status of items"
  }

  user_pool_id = aws_cognito_user_pool.pool.id
}
