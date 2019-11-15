module "ecr_repository_mets_adapter" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "mets_adapter"
}
