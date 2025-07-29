resource "aws_ecr_repository" "catalogue_graph_extractor" {
  name = "uk.ac.wellcome/catalogue_graph_extractor"
}

// This policy will expire old images in the repository, when we decide
// deployment strategy we can update this policy to match the desired tags in use
// and the number of images to keep.
resource "aws_ecr_lifecycle_policy" "expire_old_images" {
  repository = aws_ecr_repository.catalogue_graph_extractor.name
  policy = jsonencode({
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
        action = {
          type = "expire"
        }
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
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 3
        description  = "Keep dev tagged image"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["dev"]
          countType     = "imageCountMoreThan"
          countNumber   = 1
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 4
        description  = "Expire other tagged images, keep only the last 50"
        selection = {
          tagStatus   = "tagged"
          countType   = "imageCountMoreThan"
          countNumber = 50
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 5
        description  = "Expire untagged images, keep only the last 5"
        selection = {
          tagStatus   = "untagged"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
