module "merger" {
  source = "../pipeline_services/merger"

  pipeline_date = var.pipeline_date

  es_works_identified_index   = local.es_works_identified_index
  es_works_denormalised_index = local.es_works_denormalised_index
  es_images_initial_index     = local.es_images_initial_index

  batch_indexing = var.merger_batch_indexing
  queue_config = {
    # AWS guidance: 6x the function timeout (the module's 30 s default) plus the
    # batching window, so messages held while a batch fills aren't redelivered.
    visibility_timeout_seconds = 300
    max_receive_count          = 10
    batching_window_seconds    = 120
    batch_size                 = 50
    maximum_concurrency        = 30
    topic_arns = [
      module.matcher.output_topic_arn,
    ]
  }
  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }
  es_config = {
    es_host     = var.elastic.pipeline_storage_es_service_secrets["merger"]["es_host"]
    es_port     = var.elastic.pipeline_storage_es_service_secrets["merger"]["es_port"]
    es_protocol = var.elastic.pipeline_storage_es_service_secrets["merger"]["es_protocol"]
    es_apikey   = var.elastic.pipeline_storage_es_service_secrets["merger"]["es_apikey"]
  }
}
