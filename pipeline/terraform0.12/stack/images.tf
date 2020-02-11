locals {
  services = [
    "ingestor",
    "matcher",
    "merger",
    "id_minter",
    "recorder",
    "transformer_miro",
    "transformer_mets",
    "transformer_sierra",
  ]
}

data "aws_ssm_parameter" "image_ids" {
  count = length(local.services)

  name = "/catalogue_pipeline/images/${var.release_label}/${local.services[count.index]}"
}

locals {
  image_ids = zipmap(local.services, data.aws_ssm_parameter.image_ids.*.value)

  id_minter_image = local.image_ids["id_minter"]
  recorder_image  = local.image_ids["recorder"]
  matcher_image   = local.image_ids["matcher"]
  merger_image    = local.image_ids["merger"]
  ingestor_image  = local.image_ids["ingestor"]
  transformer_miro_image   = local.image_ids["transformer_miro"]
  transformer_mets_image   = local.image_ids["transformer_mets"]
  transformer_sierra_image = local.image_ids["transformer_sierra"]
}