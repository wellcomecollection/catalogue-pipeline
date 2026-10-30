resource "aws_ecr_repository" "reindexer" {
  name = "uk.ac.wellcome/reindex_worker"
}

# Keeps env tagged images (what the reindexer deploys), then only the newest 10 others.
resource "aws_ecr_lifecycle_policy" "reindexer" {
  repository = aws_ecr_repository.reindexer.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep env tagged images, e.g. env.prod"
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
