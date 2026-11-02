locals {
  calm_adapter_images = [
    "calm_adapter",
    "calm_deletion_checker",
    "calm_indexer",
  ]

  # Keeps every image a deployment can reference (env.* and latest tags), then only the newest 10 others,
  # so Inspector scans what we run rather than months of CI builds.
  ecr_lifecycle_policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep env tagged images, e.g. env.prod or env.2026-10-20"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["env."]
          countType     = "imageCountMoreThan"
          countNumber   = 20
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the latest tagged image"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["latest"]
          countType     = "imageCountMoreThan"
          countNumber   = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 3
        description  = "Expire other tagged images, keep only the last 10"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = 10
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 4
        description  = "Expire untagged images, keep only the last 5"
        selection = {
          tagStatus   = "untagged"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = { type = "expire" }
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
  policy     = local.ecr_lifecycle_policy
}

locals {
  calm_adapter_image          = "${aws_ecr_repository.calm_adapter_services["calm_adapter"].repository_url}:env.${local.release_label}"
  calm_deletion_checker_image = "${aws_ecr_repository.calm_adapter_services["calm_deletion_checker"].repository_url}:env.${local.release_label}"
  calm_indexer_image          = "${aws_ecr_repository.calm_adapter_services["calm_indexer"].repository_url}:env.${local.release_label}"
}
