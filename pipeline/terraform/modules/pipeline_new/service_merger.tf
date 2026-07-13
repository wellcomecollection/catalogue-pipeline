module "merger" {
  source = "./merger"

  pipeline_date = var.pipeline_date

  es_works_identified_index   = local.es_works_identified_index
  es_works_denormalised_index = local.es_works_denormalised_index
  es_images_initial_index     = local.es_images_initial_index
  queue_config = {
    visibility_timeout_seconds = 90
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
    es_host     = var.elastic.pipeline_storage_private_host
    es_port     = var.elastic.pipeline_storage_port
    es_protocol = var.elastic.pipeline_storage_protocol
    es_apikey   = var.elastic.pipeline_storage_es_service_secrets["merger"]["es_apikey"]
  }
}
