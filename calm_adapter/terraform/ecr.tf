resource "aws_ecr_repository" "calm_adapter" {
  name = "uk.ac.wellcome/calm_adapter"
}

resource "aws_ecr_repository" "calm_deletion_checker" {
  name = "uk.ac.wellcome/calm_deletion_checker"
}

resource "aws_ecr_repository" "calm_indexer" {
  name = "uk.ac.wellcome/calm_indexer"
}

locals {
  calm_adapter_image          = "${aws_ecr_repository.calm_adapter.repository_url}:env.${local.release_label}"
  calm_deletion_checker_image = "${aws_ecr_repository.calm_deletion_checker.repository_url}:env.${local.release_label}"
  calm_indexer_image          = "${aws_ecr_repository.calm_indexer.repository_url}:env.${local.release_label}"
}
