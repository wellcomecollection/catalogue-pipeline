locals {
  services = ["calm_adapter"]
}

data "aws_ssm_parameter" "image_ids" {
  count = length(local.services)

  name = "/calm_adapter/images/latest/${local.services[count.index]}"
}

locals {
  image_ids = zipmap(local.services, data.aws_ssm_parameter.image_ids.*.value)

  calm_adapter_image = local.image_ids["calm_adapter"]
}
