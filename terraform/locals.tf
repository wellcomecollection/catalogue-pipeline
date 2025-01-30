locals {
  namespace = "catalogue-graph"

  _extractor_task_definition_split     = split(":", module.extractor_ecs_task.task_definition_arn)
  extractor_task_definition_version    = element(local._extractor_task_definition_split, length(local._extractor_task_definition_split) - 1)
  extractor_task_definition_arn_latest = trimsuffix(module.extractor_ecs_task.task_definition_arn, ":${local.extractor_task_definition_version}")

  shared_infra = data.terraform_remote_state.shared_infra.outputs

  vpc_id          = data.terraform_remote_state.aws_account_infrastructure.outputs.developer_vpc_id
  private_subnets = data.terraform_remote_state.aws_account_infrastructure.outputs.developer_vpc_private_subnets
  public_subnets  = data.terraform_remote_state.aws_account_infrastructure.outputs.developer_vpc_public_subnets

  ec_privatelink_security_group_id = local.shared_infra["ec_developer_privatelink_sg_id"]
}

data "aws_vpc" "vpc" {
  id = local.vpc_id
}
