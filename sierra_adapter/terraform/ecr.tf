locals {
  repository_prefix = "uk.ac.wellcome"
}

resource "aws_ecr_repository" "sierra_reader" {
  name = "${local.repository_prefix}/sierra_reader"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "sierra_bib_merger" {
  name = "${local.repository_prefix}/sierra_bib_merger"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "sierra_item_merger" {
  name = "${local.repository_prefix}/sierra_item_merger"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "sierra_items_to_dynamo" {
  name = "${local.repository_prefix}/sierra_items_to_dynamo"

  lifecycle {
    prevent_destroy = true
  }
}
