# when we destroy this infra, remember to remove the apps from the deployment matrix
# .buildkite/pipeline.yml -> merger_test

module "merger_test" {
  source = "./merger"
  service_name  = "merger_test"
  
  pipeline_date = var.pipeline_date

  es_works_identified_index   = "works-identified-2026-03-06"
  es_works_denormalised_index = "works-denormalised-2025-10-09"
  es_images_initial_index     = "images-initial-2025-10-09"
  
  queue_config = {
    visibility_timeout_seconds = 90
    max_receive_count          = 10
    batching_window_seconds    = 120
    batch_size                 = 50
    maximum_concurrency        = 30
    topic_arns = [
      module.matcher_test.output_topic_arn,
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
    es_host     = module.elastic.pipeline_storage_private_host
    es_port     = module.elastic.pipeline_storage_port
    es_protocol = module.elastic.pipeline_storage_protocol
    es_apikey   = module.elastic.pipeline_storage_es_service_secrets["merger"]["es_apikey"]
  }
}