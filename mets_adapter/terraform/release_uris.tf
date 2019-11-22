locals {
  mets_adapter_image = "${module.images.services["mets_adapter"]}"
}

module "images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "mets_adapter"
  label   = "latest"

  services = ["mets_adapter"]
}
