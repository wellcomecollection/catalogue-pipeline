data "aws_ecr_repository" "service" {
  count = length(local.services)
  name  = "uk.ac.wellcome/${local.services[count.index]}"
}

locals {
  repo_urls = [for repo_url in data.aws_ecr_repository.service.*.repository_url : "${repo_url}:env.${var.release_label}"]
  image_ids = zipmap(local.services, local.repo_urls)

  matcher_image               = local.image_ids["matcher"]
  merger_image                = local.image_ids["merger"]
  inference_manager_image     = local.image_ids["inference_manager"]
  feature_inferrer_image      = local.image_ids["feature_inferrer"]
  palette_inferrer_image      = local.image_ids["palette_inferrer"]
  aspect_ratio_inferrer_image = local.image_ids["aspect_ratio_inferrer"]
  path_concatenator_image     = local.image_ids["path_concatenator"]
  ingestor_works_image        = local.image_ids["ingestor_works"]
  ingestor_images_image       = local.image_ids["ingestor_images"]
  transformer_miro_image      = local.image_ids["transformer_miro"]
  transformer_mets_image      = local.image_ids["transformer_mets"]
  transformer_tei_image       = local.image_ids["transformer_tei"]
  transformer_sierra_image    = local.image_ids["transformer_sierra"]
  transformer_calm_image      = local.image_ids["transformer_calm"]
  transformer_ebsco_image     = local.image_ids["transformer_ebsco"]
}
