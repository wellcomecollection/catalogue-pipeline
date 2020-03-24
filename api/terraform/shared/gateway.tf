# API

resource "aws_api_gateway_rest_api" "api" {
  name = "Catalogue API"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

# Resources


resource "aws_api_gateway_method" "root_resource" {
  rest_api_id = aws_api_gateway_rest_api.api.id
  resource_id = aws_api_gateway_rest_api.api.root_resource_id
  http_method = "ANY"

  authorization = "NONE"
}

module "root_resource_integration" {
  source = "git::https://github.com/wellcometrust/terraform.git//api_gateway/modules/integration/proxy?ref=v14.2.0"

  api_id        = "${aws_api_gateway_rest_api.api.id}"
  resource_id   = "${aws_api_gateway_rest_api.api.root_resource_id}"
  connection_id = "${aws_api_gateway_vpc_link.link.id}"

  hostname    = "www.example.com"
  http_method = aws_api_gateway_method.root_resource.http_method

  forward_port = "$${stageVariables.port}"
  forward_path = "catalogue/"
}

resource "aws_api_gateway_resource" "simple" {
  rest_api_id = aws_api_gateway_rest_api.api.id
  parent_id   = aws_api_gateway_rest_api.api.root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "simple_resource" {
  rest_api_id = aws_api_gateway_rest_api.api.id
  resource_id = aws_api_gateway_resource.simple.id
  http_method = "ANY"

  authorization = "NONE"

  request_parameters = {
    "method.request.path.proxy" = true
  }
}

module "simple_integration" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//api_gateway/modules/integration/proxy?ref=v14.2.0"

  api_id        = "${aws_api_gateway_rest_api.api.id}"
  resource_id   = aws_api_gateway_resource.simple.id
  connection_id = "${aws_api_gateway_vpc_link.link.id}"

  hostname    = "api.wellcomecollection.org"
  http_method = aws_api_gateway_method.simple_resource.http_method

  forward_port = "$${stageVariables.port}"
  forward_path = "catalogue/{proxy}"

  request_parameters = {
    "integration.request.path.proxy" = "method.request.path.proxy"
  }
}

# Link

resource "aws_api_gateway_vpc_link" "link" {
  name        = "${local.namespace}_vpc_link"
  target_arns = ["${module.nlb.arn}"]

  lifecycle {
    create_before_destroy = true
  }
}
