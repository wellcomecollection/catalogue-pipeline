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

moved {
  from = aws_ecr_repository.nginx_services
  to   = aws_ecr_repository.service["nginx_services"]
}
moved {
  from = aws_ecr_repository.transformer_miro
  to   = aws_ecr_repository.service["transformer_miro"]
}
moved {
  from = aws_ecr_repository.transformer_sierra
  to   = aws_ecr_repository.service["transformer_sierra"]
}
moved {
  from = aws_ecr_repository.transformer_mets
  to   = aws_ecr_repository.service["transformer_mets"]
}
moved {
  from = aws_ecr_repository.transformer_calm
  to   = aws_ecr_repository.service["transformer_calm"]
}
moved {
  from = aws_ecr_repository.transformer_tei
  to   = aws_ecr_repository.service["transformer_tei"]
}
moved {
  from = aws_ecr_repository.id_minter
  to   = aws_ecr_repository.service["id_minter"]
}
moved {
  from = aws_ecr_repository.matcher
  to   = aws_ecr_repository.service["matcher"]
}
moved {
  from = aws_ecr_repository.merger
  to   = aws_ecr_repository.service["merger"]
}
moved {
  from = aws_ecr_repository.ingestor_works
  to   = aws_ecr_repository.service["ingestor_works"]
}
moved {
  from = aws_ecr_repository.inference_manager
  to   = aws_ecr_repository.service["inference_manager"]
}
moved {
  from = aws_ecr_repository.feature_inferrer
  to   = aws_ecr_repository.service["feature_inferrer"]
}
moved {
  from = aws_ecr_repository.feature_training
  to   = aws_ecr_repository.service["feature_training"]
}
moved {
  from = aws_ecr_repository.palette_inferrer
  to   = aws_ecr_repository.service["palette_inferrer"]
}
moved {
  from = aws_ecr_repository.aspect_ratio_inferrer
  to   = aws_ecr_repository.service["aspect_ratio_inferrer"]
}
moved {
  from = aws_ecr_repository.ingestor_images
  to   = aws_ecr_repository.service["ingestor_images"]
}
moved {
  from = aws_ecr_repository.elasticdump
  to   = aws_ecr_repository.service["elasticdump"]
}
moved {
  from = aws_ecr_repository.relation_embedder
  to   = aws_ecr_repository.service["relation_embedder"]
}
moved {
  from = aws_ecr_repository.router
  to   = aws_ecr_repository.service["router"]
}
moved {
  from = aws_ecr_repository.batcher
  to   = aws_ecr_repository.service["batcher"]
}