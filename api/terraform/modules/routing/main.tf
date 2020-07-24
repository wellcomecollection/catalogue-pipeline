data "aws_route53_zone" "dotorg" {
  provider = aws.dns

  name = "wellcomecollection.org."
}

resource "aws_api_gateway_domain_name" "catalogue_api" {
  domain_name              = var.domain_name
  regional_certificate_arn = var.certificate_arn

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_route53_record" "catalogue_api" {
  provider = aws.dns

  zone_id = data.aws_route53_zone.dotorg.id
  name    = aws_api_gateway_domain_name.catalogue_api.domain_name
  type    = "A"

  alias {
    name                   = aws_api_gateway_domain_name.catalogue_api.regional_domain_name
    zone_id                = aws_api_gateway_domain_name.catalogue_api.regional_zone_id
    evaluate_target_health = false
  }
}

resource "aws_api_gateway_base_path_mapping" "catalogue_api" {
  api_id      = var.api_id
  stage_name  = var.environment
  domain_name = aws_api_gateway_domain_name.catalogue_api.domain_name
  base_path   = "catalogue"
}

resource "aws_api_gateway_deployment" "stage" {
  rest_api_id = var.api_id

  # If we specify the stage name here then API Gateway tries to create it even
  # if it already exists (from below). Setting it to an empty string prevents this.
  # See https://github.com/terraform-providers/terraform-provider-aws/issues/2918#issuecomment-356684239
  stage_name = ""

  variables = {
    port = var.listener_port
  }

  stage_description = filemd5("${path.module}/../../shared/gateway.tf")

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "stage" {
  stage_name    = var.environment
  rest_api_id   = var.api_id
  deployment_id = aws_api_gateway_deployment.stage.id
  variables = {
    port = var.listener_port
  }
}
