data "aws_ecr_repository" "feature_training" {
  name  = "uk.ac.wellcome/feature_training"
}

locals {
  feature_training_image = "${data.aws_ecr_repository.feature_training.repository_url}:env.${var.release_label}"
}
