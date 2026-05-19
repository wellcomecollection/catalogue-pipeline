module "matcher" {
  source = "./matcher"

  pipeline_date = var.pipeline_date

  es_works_identified_index = local.es_works_identified_index
  scale_up_matcher_db       = var.reindexing_state.scale_up_matcher_db

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  secret_env_vars = module.elastic.pipeline_storage_es_service_secrets["matcher"]

  queue_config = {
    visibility_timeout_seconds = 90
    max_receive_count          = 10
    batching_window_seconds    = 30
    batch_size                 = var.reindexing_state.scale_up_matcher_db ? 400 : 100
    maximum_concurrency        = var.reindexing_state.scale_up_matcher_db ? 40 : 2
    topic_arns = [
      module.id_minter_lambda.id_minter_output_topic_arn,
    ]
  }

  timeout     = var.reindexing_state.scale_up_matcher_db ? 300 : 30
  memory_size = var.reindexing_state.scale_up_matcher_db ? 4096 : 1024
}
