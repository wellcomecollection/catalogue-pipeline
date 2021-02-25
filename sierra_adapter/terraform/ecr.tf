locals {
  repository_prefix = "uk.ac.wellcome"
}

resource "aws_ecr_repository" "sierra_reader" {
  name = "${local.repository_prefix}/sierra_reader"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "sierra_merger" {
  name = "${local.repository_prefix}/sierra_merger"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "sierra_linker" {
  name = "${local.repository_prefix}/sierra_linker"

  lifecycle {
    prevent_destroy = true
  }
}
