locals {
  tei_adapter_images = [
    "tei_id_extractor",
    "tei_adapter",
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

moved {
  from = aws_ecr_repository.ecr_repository_tei_id_extractor
  to   = aws_ecr_repository.tei_adapter_services["tei_id_extractor"]
}

moved {
  from = aws_ecr_repository.ecr_repository_tei_adapter
  to   = aws_ecr_repository.tei_adapter_services["tei_adapter"]
}

resource "aws_ecr_repository" "tei_adapter_services" {
  for_each = toset(local.tei_adapter_images)

  name = "uk.ac.wellcome/${each.key}"
}

resource "aws_ecr_lifecycle_policy" "tei_adapter_services" {
  for_each = aws_ecr_repository.tei_adapter_services

  repository = each.value.name
  policy     = local.ecr_policy_only_keep_the_last_100_images
}

locals {
  tei_id_extractor_image = "${aws_ecr_repository.tei_adapter_services["tei_id_extractor"].repository_url}:env.${local.release_label}"
  tei_adapter_image      = "${aws_ecr_repository.tei_adapter_services["tei_adapter"].repository_url}:env.${local.release_label}"
}
