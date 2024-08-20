locals {
  namespace = "ebsco-adapter"

  catalogue_vpcs = data.terraform_remote_state.accounts_catalogue.outputs
  shared_infra   = data.terraform_remote_state.shared_infra.outputs

  _task_definition_split     = split(":", module.ftp_task.task_definition_arn)
  task_definition_version    = element(local._task_definition_split, length(local._task_definition_split) - 1)
  task_definition_arn_latest = trimsuffix(module.ftp_task.task_definition_arn, ":${local.task_definition_version}")

  network_config = {
    vpc_id  = local.catalogue_vpcs["catalogue_vpc_delta_id"]
    subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

    ec_privatelink_security_group_id = local.shared_infra["ec_platform_privatelink_sg_id"]
  }

  reindexer_topic_arn = data.terraform_remote_state.reindexer.outputs.ebsco_reindexer_topic_arn
}
