resource "aws_ecr_repository" "catalogue_graph_extractor" {
  name = "uk.ac.wellcome/catalogue_graph_extractor"
}

// This policy will expire old images in the repository, when we decide
// deployment strategy we can update this policy to match the desired tags in use
// and the number of images to keep.
resource "aws_ecr_lifecycle_policy" "expire_old_images" {
  repository = aws_ecr_repository.catalogue_graph_extractor.name
  policy     = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Only keep the last 25 images in a repo"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 25
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}