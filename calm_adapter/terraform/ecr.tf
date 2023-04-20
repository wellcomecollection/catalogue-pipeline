locals {
  calm_adapter_images = [
    "calm_adapter",
    "calm_deletion_checker",
    "calm_indexer",
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

resource "aws_ecr_repository" "calm_adapter_services" {
  for_each = toset(local.calm_adapter_images)

  name = "uk.ac.wellcome/${each.key}"
}

resource "aws_ecr_lifecycle_policy" "calm_adapter_services" {
  for_each = aws_ecr_repository.calm_adapter_services

  repository = each.value.name
  policy     = local.ecr_policy_only_keep_the_last_100_images
}

locals {
  calm_adapter_image          = "${aws_ecr_repository.calm_adapter_services["calm_adapter"].repository_url}:env.${local.release_label}"
  calm_deletion_checker_image = "${aws_ecr_repository.calm_adapter_services["calm_deletion_checker"].repository_url}:env.${local.release_label}"
  calm_indexer_image          = "${aws_ecr_repository.calm_adapter_services["calm_indexer"].repository_url}:env.${local.release_label}"
}
