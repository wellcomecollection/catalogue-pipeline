module "prod_images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "catalogue_api"
  label   = "prod"

  services = "${local.service_repositories}"

  providers = {
    aws = "aws.platform_account"
  }
}

module "staging_images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "catalogue_api"
  label   = "staging"

  services = "${local.service_repositories}"

  providers = {
    aws = "aws.platform_account"
  }
}

module "latest_images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "catalogue_api"
  label   = "latest"

  services = "${local.service_repositories}"

  providers = {
    aws = "aws.platform_account"
  }
}
