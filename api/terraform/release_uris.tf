module "prod_images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "catalogue_api"
  label   = "prod"

  services = [
    "api",
    "nginx_api-gw",
    "snapshot_generator",
    "update_api_docs",
  ]
}

module "staging_images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "catalogue_api"
  label   = "staging"

  services = [
    "api",
    "nginx_api-gw",
    "snapshot_generator",
    "update_api_docs",
  ]
}

module "latest_images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "catalogue_api"
  label   = "latest"

  services = [
    "api",
    "nginx_api-gw",
    "snapshot_generator",
    "update_api_docs",
  ]
}
