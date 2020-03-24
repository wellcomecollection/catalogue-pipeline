module "ecr_repository_nginx_api-gw" {
  source    = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v19.5.1"
  id        = "nginx_api-gw"
  namespace = "uk.ac.wellcome"
}

module "ecr_repository_api" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "api"
}

module "ecr_repository_update_api_docs" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "update_api_docs"
}
