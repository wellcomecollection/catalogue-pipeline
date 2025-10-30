locals {
  namespace = "catalogue-graph"

  shared_infra = data.terraform_remote_state.shared_infra.outputs

  vpc_id          = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_private_subnets
  public_subnets  = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_public_subnets
}

data "aws_vpc" "vpc" {
  id = local.vpc_id
}
