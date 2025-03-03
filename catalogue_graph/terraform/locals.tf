locals {
  namespace = "catalogue-graph"

  _extractor_task_definition_split     = split(":", module.extractor_ecs_task.task_definition_arn)
  extractor_task_definition_version    = element(local._extractor_task_definition_split, length(local._extractor_task_definition_split) - 1)
  extractor_task_definition_arn_latest = trimsuffix(module.extractor_ecs_task.task_definition_arn, ":${local.extractor_task_definition_version}")

  shared_infra = data.terraform_remote_state.shared_infra.outputs

  vpc_id          = data.terraform_remote_state.catalogue_aws_account_infrastructure.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.catalogue_aws_account_infrastructure.outputs.catalogue_vpc_delta_private_subnets
  public_subnets  = data.terraform_remote_state.catalogue_aws_account_infrastructure.outputs.catalogue_vpc_delta_public_subnets

  ec_privatelink_security_group_id = local.shared_infra["ec_platform_privatelink_sg_id"]

  catalogue_graph_nlb_url = "catalogue-graph.wellcomecollection.org"

  # This is a hint, the ingestors might need to be in the pipeline stack!
  pipeline_date = "2024-11-18"
}

data "aws_vpc" "vpc" {
  id = local.vpc_id
}

data "archive_file" "empty_zip" {
  output_path = "data/empty.zip"
  type        = "zip"
  source {
    content  = "// This file is intentionally left empty"
    filename = "lambda.py"
  }
}
