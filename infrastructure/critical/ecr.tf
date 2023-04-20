locals {
  images = [
    "nginx_services",
    "transformer_miro",
    "transformer_sierra",
    "transformer_mets",
    "transformer_calm",
    "transformer_tei",
    "id_minter",
    "matcher",
    "merger",
    "ingestor_works",
    "inference_manager",
    "feature_inferrer",
    "feature_training",
    "palette_inferrer",
    "aspect_ratio_inferrer",
    "ingestor_images",
    "elasticdump",
    "relation_embedder",
    "router",
    "batcher",
  ]
}

resource "aws_ecr_repository" "service" {
  for_each = toset(local.images)

  name = "uk.ac.wellcome/${each.key}"
}
