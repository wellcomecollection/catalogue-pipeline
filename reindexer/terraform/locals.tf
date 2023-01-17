locals {
  environment = "prod"

  vhs_sierra_table_name  = data.terraform_remote_state.sierra_adapter.outputs.vhs_table_name
  vhs_miro_table_name    = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_miro_table_name
  mets_dynamo_table_name = data.terraform_remote_state.mets_adapter.outputs.mets_dynamo_table_name
  tei_dynamo_table_name  = data.terraform_remote_state.tei_adapter.outputs.tei_adapter_dynamo_table_name
  vhs_calm_table_name    = data.terraform_remote_state.calm_adapter.outputs.vhs_table_name

  reporting_miro_reindex_topic_arn   = data.terraform_remote_state.shared_infra.outputs.reporting_miro_reindex_topic_arn
  reporting_sierra_reindex_topic_arn = data.terraform_remote_state.shared_infra.outputs.reporting_sierra_reindex_topic_arn
  catalogue_miro_reindex_topic_arn   = data.terraform_remote_state.shared_infra.outputs.catalogue_miro_reindex_topic_arn
  catalogue_sierra_reindex_topic_arn = data.terraform_remote_state.shared_infra.outputs.catalogue_sierra_reindex_topic_arn
  mets_reindexer_topic_name          = module.mets_reindexer_topic.name
  mets_reindexer_topic_arn           = module.mets_reindexer_topic.arn
  tei_reindexer_topic_arn            = module.tei_reindexer_topic.arn
  calm_reindexer_topic_name          = module.calm_reindexer_topic.name
  calm_reindexer_topic_arn           = module.calm_reindexer_topic.arn
  calm_deletion_checker_topic_name   = module.calm_deletion_checker_topic.name
  calm_deletion_checker_topic_arn    = module.calm_deletion_checker_topic.arn
  miro_updates_topic_arn             = data.terraform_remote_state.shared_infra.outputs.miro_updates_topic_arn

  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
  dlq_alarm_arn   = data.terraform_remote_state.monitoring.outputs.platform_dlq_alarm_topic_arn

  reindex_worker_image = "${aws_ecr_repository.reindexer.repository_url}:env.${local.environment}"

  # This map defines the possible reindexer configurations.
  #
  # The key is the "ID" that can be used to trigger a reindex, and the table/topic
  # are the DynamoDB table that will be reindexed, and the topic ARN to send
  # new records to, respectively.
  #
  reindexer_jobs = [
    {
      source      = "sierra"
      destination = "reporting"
      table       = local.vhs_sierra_table_name
      topic       = local.reporting_sierra_reindex_topic_arn
    },
    {
      source      = "sierra"
      destination = "catalogue"
      table       = local.vhs_sierra_table_name
      topic       = local.catalogue_sierra_reindex_topic_arn
    },
    {
      source      = "miro"
      destination = "reporting"
      table       = local.vhs_miro_table_name
      topic       = local.reporting_miro_reindex_topic_arn
    },
    {
      source      = "miro"
      destination = "catalogue"
      table       = local.vhs_miro_table_name
      topic       = local.catalogue_miro_reindex_topic_arn
    },
    {
      source      = "miro"
      destination = "catalogue_miro_updates"
      table       = local.vhs_miro_table_name
      topic       = local.miro_updates_topic_arn
    },
    {
      source      = "mets"
      destination = "catalogue"
      table       = local.mets_dynamo_table_name
      topic       = local.mets_reindexer_topic_arn
    },
    {
      source      = "tei"
      destination = "catalogue"
      table       = local.tei_dynamo_table_name
      topic       = local.tei_reindexer_topic_arn
    },
    {
      source      = "calm"
      destination = "catalogue"
      table       = local.vhs_calm_table_name
      topic       = local.calm_reindexer_topic_arn
    },
    {
      source      = "calm"
      destination = "calm_deletion_checker"
      table       = local.vhs_calm_table_name
      topic       = local.calm_deletion_checker_topic_arn
    },
    {
      source      = "calm"
      destination = "reporting"
      table       = local.vhs_calm_table_name
      topic       = aws_sns_topic.calm_reindex_reporting.arn
    },
  ]
}
