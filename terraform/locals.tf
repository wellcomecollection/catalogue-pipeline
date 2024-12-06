locals {
  vpc_id          = data.terraform_remote_state.aws_account_infrastructure.outputs.developer_vpc_id
  private_subnets = data.terraform_remote_state.aws_account_infrastructure.outputs.developer_vpc_private_subnets
  public_subnets  = data.terraform_remote_state.aws_account_infrastructure.outputs.developer_vpc_public_subnets
}

data "aws_vpc" "vpc" {
  id = local.vpc_id
}
