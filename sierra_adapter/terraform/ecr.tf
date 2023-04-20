locals {
  sierra_adapter_images = [
    "sierra_reader",
    "sierra_merger",
    "sierra_linker",
    "sierra_indexer",
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

resource "aws_ecr_repository" "sierra_adapter_services" {
  for_each = toset(local.sierra_adapter_images)

  name = "uk.ac.wellcome/${each.key}"
}

resource "aws_ecr_lifecycle_policy" "sierra_adapter_services" {
  for_each = aws_ecr_repository.sierra_adapter_services

  repository = each.value.name
  policy     = local.ecr_policy_only_keep_the_last_100_images
}
