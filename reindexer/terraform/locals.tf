locals {
  environment = "prod"

  vhs_sierra_table_name         = data.terraform_remote_state.sierra_adapter.outputs.vhs_table_name
  vhs_miro_table_name           = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_miro_table_name
  vhs_miro_inventory_table_name = data.terraform_remote_state.catalogue_infra_critical.outputs.vhs_miro_inventory_table_name
  mets_dynamo_table_name        = data.terraform_remote_state.mets_adapter.outputs.mets_dynamo_table_name
  vhs_calm_table_name           = data.terraform_remote_state.calm_adapter.outputs.vhs_table_name

  reporting_miro_hybrid_records_topic_arn           = data.terraform_remote_state.shared_infra.outputs.reporting_miro_reindex_topic_arn
  reporting_miro_inventory_hybrid_records_topic_arn = data.terraform_remote_state.shared_infra.outputs.reporting_miro_inventory_reindex_topic_arn
  reporting_sierra_hybrid_records_topic_arn         = data.terraform_remote_state.shared_infra.outputs.reporting_sierra_reindex_topic_arn
  catalogue_miro_hybrid_records_topic_arn           = data.terraform_remote_state.shared_infra.outputs.catalogue_miro_reindex_topic_arn
  catalogue_sierra_hybrid_records_topic_arn         = data.terraform_remote_state.shared_infra.outputs.catalogue_sierra_reindex_topic_arn
  mets_reindexer_topic_name                         = module.mets_reindexer_topic.name
  mets_reindexer_topic_arn                          = module.mets_reindexer_topic.arn
  calm_reindexer_topic_name                         = module.calm_reindexer_topic.name
  calm_reindexer_topic_arn                          = module.calm_reindexer_topic.arn

  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
  dlq_alarm_arn   = data.terraform_remote_state.shared_infra.outputs.dlq_alarm_arn

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
      topic       = local.reporting_sierra_hybrid_records_topic_arn
    },
    {
      source      = "sierra"
      destination = "catalogue"
      table       = local.vhs_sierra_table_name
      topic       = local.catalogue_sierra_hybrid_records_topic_arn
    },
    {
      source      = "miro"
      destination = "reporting"
      table       = local.vhs_miro_table_name
      topic       = local.reporting_miro_hybrid_records_topic_arn
    },
    {
      source      = "miro"
      destination = "catalogue"
      table       = local.vhs_miro_table_name
      topic       = local.catalogue_miro_hybrid_records_topic_arn
    },
    {
      source      = "miro_inventory"
      destination = "reporting"
      table       = local.vhs_miro_inventory_table_name
      topic       = local.reporting_miro_inventory_hybrid_records_topic_arn
    },
    {
      source      = "mets"
      destination = "catalogue"
      table       = local.mets_dynamo_table_name
      topic       = local.mets_reindexer_topic_arn
    },
    {
      source      = "calm"
      destination = "catalogue"
      table       = local.vhs_calm_table_name
      topic       = local.calm_reindexer_topic_arn
    },
  ]
}
