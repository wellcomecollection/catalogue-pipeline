resource "aws_ecr_repository" "ecr_repository_calm_adapter" {
  name = "uk.ac.wellcome/calm_adapter"
}

resource "aws_ecr_repository" "ecr_repository_calm_deletion_checker" {
  name = "uk.ac.wellcome/calm_deletion_checker"
}
