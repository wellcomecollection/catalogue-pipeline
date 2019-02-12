locals {
  goobi_reader_image = "${module.images.services["goobi_reader"]}"
}

module "images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "goobi_adapter"
  label   = "latest"

  services = [
    "goobi_reader",
  ]
}
