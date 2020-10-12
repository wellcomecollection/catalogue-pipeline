locals {
  services = [
    "ingestor_works",
    "ingestor_images",
    "matcher",
    "merger",
    "id_minter_works",
    "id_minter_images",
    "inference_manager",
    "feature_inferrer",
    "feature_training",
    "palette_inferrer",
    "recorder",
    "transformer_miro",
    "transformer_mets",
    "transformer_sierra",
    "transformer_calm",
  ]
}

data "aws_ssm_parameter" "inferrer_lsh_model_key" {
  name = "/catalogue_pipeline/config/models/${var.release_label}/lsh_model"
}

data "aws_ssm_parameter" "latest_lsh_model_key" {
  name = "/catalogue_pipeline/config/models/latest/lsh_model"
}

data "aws_ecr_repository" "service" {
  count = length(local.services)
  name  = "uk.ac.wellcome/${local.services[count.index]}"
}

locals {
  repo_urls = [for repo_url in data.aws_ecr_repository.service.*.repository_url : "${repo_url}:env.${var.release_label}"]
  image_ids = zipmap(local.services, local.repo_urls)

  id_minter_images_image   = local.image_ids["id_minter_images"]
  id_minter_works_image    = local.image_ids["id_minter_works"]
  recorder_image           = local.image_ids["recorder"]
  matcher_image            = local.image_ids["matcher"]
  merger_image             = local.image_ids["merger"]
  inference_manager_image  = local.image_ids["inference_manager"]
  feature_inferrer_image   = local.image_ids["feature_inferrer"]
  feature_training_image   = local.image_ids["feature_training"]
  palette_inferrer_image   = local.image_ids["palette_inferrer"]
  ingestor_works_image     = local.image_ids["ingestor_works"]
  ingestor_images_image    = local.image_ids["ingestor_images"]
  transformer_miro_image   = local.image_ids["transformer_miro"]
  transformer_mets_image   = local.image_ids["transformer_mets"]
  transformer_sierra_image = local.image_ids["transformer_sierra"]
  transformer_calm_image   = local.image_ids["transformer_calm"]
}
