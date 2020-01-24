module "ecr_repository_calm_adapter" {
  source = "git::https://github.com/wellcometrust/terraform.git//ecr?ref=v1.0.0"
  name   = "calm_adapter"
}
