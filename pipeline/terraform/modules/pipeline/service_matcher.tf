module "matcher" {
  source = "../pipeline_services/matcher"

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
    # Twice the lambda timeout: enough that an in-flight batch is never redelivered,
    # but deliberately below the AWS 6x guidance. Works waiting on a DynamoDB lock
    # (60 s expiry) are retried by visibility expiry, so a 6x value would make
    # every lock conflict wait 570 s per attempt and slow merge convergence.
    visibility_timeout_seconds = var.reindexing_state.scale_up_matcher_db ? 600 : 180
    max_receive_count          = 10
    batching_window_seconds    = 30
    batch_size                 = var.reindexing_state.scale_up_matcher_db ? 400 : 100
    maximum_concurrency        = var.reindexing_state.scale_up_matcher_db ? 40 : 2
    report_batch_item_failures = true
    topic_arns = [
      module.id_minter_lambda.id_minter_output_topic_arn,
    ]
  }

  # A full 100-message batch takes close to 30 s at steady state
  timeout     = var.reindexing_state.scale_up_matcher_db ? 300 : 90
  memory_size = var.reindexing_state.scale_up_matcher_db ? 4096 : 1024
}
