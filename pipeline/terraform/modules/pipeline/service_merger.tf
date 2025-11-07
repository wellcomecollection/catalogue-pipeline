locals {
  service_vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  queue_config = {
    visibility_timeout_seconds = local.queue_visibility_timeout_seconds
    max_receive_count          = local.max_receive_count
    batching_window_seconds    = 120
    batch_size                 = 50
    maximum_concurrency        = 30
    topic_arns = [
      module.matcher_output_topic.arn,
    ]
  }

  es_config = {
    es_host     = module.elastic.pipeline_storage_private_host
    es_port     = module.elastic.pipeline_storage_port
    es_protocol = module.elastic.pipeline_storage_protocol
    es_apikey   = module.elastic.pipeline_storage_es_service_secrets["merger"]["es_apikey"]
  }
}

module "merger" {
  source = "./merger"

  pipeline_date = var.pipeline_date
  vpc_config    = local.service_vpc_config
  queue_config  = local.queue_config
  es_config     = local.es_config
}

module "merger_delta" {
  source = "./merger"

  pipeline_date = var.pipeline_date
  index_date    = "2025-10-09"

  vpc_config   = local.service_vpc_config
  queue_config = local.queue_config
  es_config    = local.es_config

  es_index_config = {
    es_works_identified_index = local.es_works_identified_index
  }
}