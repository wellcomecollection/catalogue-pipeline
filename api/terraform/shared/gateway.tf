# API

resource "aws_api_gateway_rest_api" "catalogue_api" {
  name = "Catalogue API"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

# Resources

resource "aws_api_gateway_method" "root_resource" {
  rest_api_id = aws_api_gateway_rest_api.catalogue_api.id
  resource_id = aws_api_gateway_rest_api.catalogue_api.root_resource_id
  http_method = "ANY"

  authorization = "NONE"
}

resource "aws_api_gateway_integration" "root" {
  rest_api_id = aws_api_gateway_rest_api.catalogue_api.id
  resource_id = aws_api_gateway_rest_api.catalogue_api.root_resource_id
  http_method = aws_api_gateway_method.root_resource.http_method

  integration_http_method = "ANY"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_api_gateway_vpc_link.link.id
  uri                     = "http://www.example.com:$${stageVariables.port}/catalogue/"
}

resource "aws_api_gateway_resource" "simple" {
  rest_api_id = aws_api_gateway_rest_api.catalogue_api.id
  parent_id   = aws_api_gateway_rest_api.catalogue_api.root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "simple_resource" {
  rest_api_id = aws_api_gateway_rest_api.catalogue_api.id
  resource_id = aws_api_gateway_resource.simple.id
  http_method = "ANY"

  authorization = "NONE"

  request_parameters = {
    "method.request.path.proxy" = true
  }
}

resource "aws_api_gateway_integration" "simple" {
  rest_api_id = aws_api_gateway_rest_api.catalogue_api.id
  resource_id = aws_api_gateway_resource.simple.id
  http_method = aws_api_gateway_method.simple_resource.http_method

  integration_http_method = "ANY"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_api_gateway_vpc_link.link.id
  uri                     = "http://api.wellcomecollection.org:$${stageVariables.port}/catalogue/{proxy}"

  request_parameters = {
    "integration.request.path.proxy" = "method.request.path.proxy"
  }
}

# Link

resource "aws_api_gateway_vpc_link" "link" {
  name        = "${local.namespace}_vpc_link"
  target_arns = [aws_lb.catalogue_api.arn]

  lifecycle {
    create_before_destroy = true
  }
}
