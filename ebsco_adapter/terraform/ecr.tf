locals {
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

resource "aws_ecr_repository" "ebsco_adapter" {
  name = "uk.ac.wellcome/ebsco_adapter"
}

resource "aws_ecr_lifecycle_policy" "ebsco_adapter" {
  repository = aws_ecr_repository.ebsco_adapter.name
  policy     = local.ecr_policy_only_keep_the_last_100_images
}
