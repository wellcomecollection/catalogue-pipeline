data "aws_ssm_parameter" "api_container_image" {
  provider = aws.platform

  name = "/catalogue_api/images/${var.environment}/api"
}

locals {
  api_container_image   = data.aws_ssm_parameter.api_container_image.value
}
