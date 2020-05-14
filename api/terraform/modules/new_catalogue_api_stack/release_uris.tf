data "aws_ssm_parameter" "api_container_image" {
  provider = aws.platform

  name = "/catalogue_api/images/${var.environment}/api"
}

data "aws_ssm_parameter" "nginx_container_image" {
  provider = aws.platform

  name = "/catalogue_api/images/${var.environment}/nginx_api-gw"
}

locals {
  api_container_image   = data.aws_ssm_parameter.api_container_image.value
  nginx_container_image = data.aws_ssm_parameter.nginx_container_image.value
}
