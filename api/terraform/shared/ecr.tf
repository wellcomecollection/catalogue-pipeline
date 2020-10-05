resource "aws_ecr_repository" "api" {
  name = "uk.ac.wellcome/api"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "nginx_api_gw" {
  name = "uk.ac.wellcome/nginx_api_gw"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "snapshot_generator" {
  name = "uk.ac.wellcome/snapshot_generator"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "update_api_docs" {
  name = "uk.ac.wellcome/update_api_docs"

  lifecycle {
    prevent_destroy = true
  }
}

# These are the ECR repositories in the *platform* account, where new images
# are currently published.  Eventually we should publish images into the catalogue
# account and remove these repositories, but that's not done yet.

resource "aws_ecr_repository" "platform_api" {
  provider = aws.platform

  name = "uk.ac.wellcome/api"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "platform_nginx_api_gw" {
  provider = aws.platform

  name = "uk.ac.wellcome/nginx_api-gw"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "platform_snapshot_generator" {
  provider = aws.platform

  name = "uk.ac.wellcome/snapshot_generator"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_repository" "platform_update_api_docs" {
  provider = aws.platform

  name = "uk.ac.wellcome/update_api_docs"

  lifecycle {
    prevent_destroy = true
  }
}
