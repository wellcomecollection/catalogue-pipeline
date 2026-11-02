locals {
  tei_adapter_images = [
    "tei_id_extractor",
    "tei_adapter",
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


resource "aws_ecr_repository" "tei_adapter_services" {
  for_each = toset(local.tei_adapter_images)

  name = "uk.ac.wellcome/${each.key}"
}

resource "aws_ecr_lifecycle_policy" "tei_adapter_services" {
  for_each = aws_ecr_repository.tei_adapter_services

  repository = each.value.name
  policy     = local.ecr_lifecycle_policy
}

locals {
  tei_id_extractor_image = "${aws_ecr_repository.tei_adapter_services["tei_id_extractor"].repository_url}:env.${local.release_label}"
  tei_adapter_image      = "${aws_ecr_repository.tei_adapter_services["tei_adapter"].repository_url}:env.${local.release_label}"
}
