locals {
  services = [
    "ingestor_works",
    "ingestor_images",
    "matcher",
    "merger",
    "id_minter",
    "inference_manager",
    "feature_inferrer",
    "recorder",
    "transformer_miro",
    "transformer_mets",
    "transformer_sierra",
    "transformer_calm",
  ]
}

data "aws_ssm_parameter" "image_ids" {
  count = length(local.services)

  name = "/catalogue_pipeline/images/${var.release_label}/${local.services[count.index]}"
}

locals {
  image_ids = zipmap(local.services, data.aws_ssm_parameter.image_ids.*.value)

  id_minter_image          = local.image_ids["id_minter"]
  recorder_image           = local.image_ids["recorder"]
  matcher_image            = local.image_ids["matcher"]
  merger_image             = local.image_ids["merger"]
  inference_manager_image  = local.image_ids["inference_manager"]
  feature_inferrer_image   = local.image_ids["feature_inferrer"]
  ingestor_works_image     = local.image_ids["ingestor_works"]
  ingestor_images_image     = local.image_ids["ingestor_images"]
  transformer_miro_image   = local.image_ids["transformer_miro"]
  transformer_mets_image   = local.image_ids["transformer_mets"]
  transformer_sierra_image = local.image_ids["transformer_sierra"]
  transformer_calm_image   = local.image_ids["transformer_calm"]
}
