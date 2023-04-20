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

  ecr_policy_only_keep_the_last_100_images = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Only keep the last 100 images in a repo"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 100
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_ecr_repository" "service" {
  for_each = toset(local.images)

  name = "uk.ac.wellcome/${each.key}"
}

resource "aws_ecr_lifecycle_policy" "content_webapp" {
  for_each = aws_ecr_repository.service

  repository = each.value.name
  policy     = local.ecr_policy_only_keep_the_last_100_images
}
