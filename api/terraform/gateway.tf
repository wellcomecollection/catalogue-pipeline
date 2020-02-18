# API

resource "aws_api_gateway_rest_api" "api" {
  name = "Catalogue API"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

# Base path mappings
resource "aws_api_gateway_base_path_mapping" "catalogue_prod" {
  api_id      = aws_api_gateway_rest_api.api.id
  stage_name  = local.prod_name
  domain_name = aws_api_gateway_domain_name.prod.domain_name
  base_path   = "catalogue"
}

resource "aws_api_gateway_base_path_mapping" "catalogue_staging" {
  api_id      = aws_api_gateway_rest_api.api.id
  stage_name  = local.staging_name
  domain_name = aws_api_gateway_domain_name.staging.domain_name
  base_path   = "catalogue"
}

# Resources

module "root_resource_method" {
  source = "git::https://github.com/wellcometrust/terraform.git//api_gateway/modules/method?ref=v14.2.0"

  api_id      = aws_api_gateway_rest_api.api.id
  resource_id = aws_api_gateway_rest_api.api.root_resource_id
}

module "root_resource_integration" {
  source = "git::https://github.com/wellcometrust/terraform.git//api_gateway/modules/integration/proxy?ref=v14.2.0"

  api_id        = aws_api_gateway_rest_api.api.id
  resource_id   = aws_api_gateway_rest_api.api.root_resource_id
  connection_id = aws_api_gateway_vpc_link.link.id

  hostname    = "www.example.com"
  http_method = module.root_resource_method.http_method

  forward_port = "$${stageVariables.port}"
  forward_path = "catalogue/"
}

module "simple_resource" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//api_gateway/modules/resource?ref=v14.2.0"

  api_id = aws_api_gateway_rest_api.api.id

  parent_id = aws_api_gateway_rest_api.api.root_resource_id
  path_part = "{proxy+}"

  request_parameters = {
    "method.request.path.proxy" = true
  }
}

module "simple_integration" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//api_gateway/modules/integration/proxy?ref=v14.2.0"

  api_id        = aws_api_gateway_rest_api.api.id
  resource_id   = module.simple_resource.resource_id
  connection_id = aws_api_gateway_vpc_link.link.id

  hostname    = "api.wellcomecollection.org"
  http_method = module.simple_resource.http_method

  forward_port = "$${stageVariables.port}"
  forward_path = "catalogue/{proxy}"

  request_parameters = {
    integration.request.path.proxy = "method.request.path.proxy"
  }
}

# Link

resource "aws_api_gateway_vpc_link" "link" {
  name = "${local.namespace}_vpc_link"
  # TF-UPGRADE-TODO: In Terraform v0.10 and earlier, it was sometimes necessary to
  # force an interpolation expression to be interpreted as a list by wrapping it
  # in an extra set of list brackets. That form was supported for compatibility in
  # v0.11, but is no longer supported in Terraform v0.12.
  #
  # If the expression in the following list itself returns a list, remove the
  # brackets to avoid interpretation as a list of lists. If the expression
  # returns a single list item then leave it as-is and remove this TODO comment.
  target_arns = [module.nlb.arn]

  lifecycle {
    create_before_destroy = true
  }
}

