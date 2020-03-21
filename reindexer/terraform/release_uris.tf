locals {
  reindex_worker_image = module.images.services["reindex_worker"]
}

module "images" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecs/modules/images?ref=v19.8.0"

  project = "reindexer"
  label   = "latest"

  services = [
    "reindex_worker",
  ]
}
