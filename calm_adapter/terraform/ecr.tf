resource "aws_ecr_repository" "ecr_repository_calm_adapter" {
  name = "uk.ac.wellcome/calm_adapter"
}

resource "aws_ecr_repository" "ecr_repository_calm_deletion_checker" {
  name = "uk.ac.wellcome/calm_deletion_checker"
}

locals {
  calm_adapter_image          = "${aws_ecr_repository.ecr_repository_calm_adapter.repository_url}:env.prod"
  calm_deletion_checker_image = "${aws_ecr_repository.ecr_repository_calm_deletion_checker.repository_url}:env.prod"
}
