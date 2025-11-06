resource "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

resource "aws_ecr_repository" "unified_pipeline_task" {
  name = "uk.ac.wellcome/unified_pipeline_task"
}

// Shared lifecycle policy JSON for both repositories
locals {
  ecr_lifecycle_policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep prod tagged image"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["prod"]
          countType     = "imageCountMoreThan"
          countNumber   = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep latest tagged image"
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
        description  = "Keep latest 4 env tagged images e.g. env.2025-10-01"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["env"]
          countType     = "imageCountMoreThan"
          countNumber   = 4
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 4
        description  = "Keep dev tagged image"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["dev"]
          countType     = "imageCountMoreThan"
          countNumber   = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 5
        description  = "Expire other tagged images, keep only the last 50"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = 50
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 6
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

resource "aws_ecr_lifecycle_policy" "expire_old_images_unified_pipeline_lambda" {
  repository = aws_ecr_repository.unified_pipeline_lambda.name
  policy     = local.ecr_lifecycle_policy
}

resource "aws_ecr_lifecycle_policy" "expire_old_images_unified_pipeline_task" {
  repository = aws_ecr_repository.unified_pipeline_task.name
  policy     = local.ecr_lifecycle_policy
}