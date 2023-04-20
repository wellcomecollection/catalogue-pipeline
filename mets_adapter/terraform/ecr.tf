locals {
  mets_adapter_images = [
    "mets_adapter",
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
  from = aws_ecr_repository.mets_adapter
  to   = aws_ecr_repository.mets_adapter_services["mets_adapter"]
}

resource "aws_ecr_repository" "mets_adapter_services" {
  for_each = toset(local.mets_adapter_images)

  name = "uk.ac.wellcome/${each.key}"
}

resource "aws_ecr_lifecycle_policy" "mets_adapter_services" {
  for_each = aws_ecr_repository.mets_adapter_services

  repository = each.value.name
  policy     = local.ecr_policy_only_keep_the_last_100_images
}
