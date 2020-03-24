module "ecr_repository_api" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "api"
}

module "ecr_repository_nginx_api_gw" {
  source    = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v19.5.1"
  id        = "nginx_api_gw"
  namespace = "uk.ac.wellcome"
}

module "ecr_repository_snapshot_generator" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "snapshot_generator"
}

module "ecr_repository_update_api_docs" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "update_api_docs"
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
