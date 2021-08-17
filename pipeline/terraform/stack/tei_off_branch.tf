module "tei_off_branch" {
  source = "./branch"

  toggle_tei_on = false

  depends_on                       = [aws_ecs_cluster.cluster]
  matcher_topic_arn                = module.matcher_topic.arn
  is_reindexing                    = var.is_reindexing
  dlq_alarm_arn                    = var.dlq_alarm_arn
  ec_privatelink_security_group_id = var.ec_privatelink_security_group_id
  release_label                    = var.release_label
  shared_logging_secrets           = var.shared_logging_secrets
  subnets                          = var.subnets

  inferrer_model_data_bucket_name = var.inferrer_model_data_bucket_name
  min_capacity                    = var.min_capacity
  max_capacity                    = local.max_capacity

  batcher_image               = local.batcher_image
  router_image                = local.router_image
  relation_embedder_image     = local.relation_embedder_image
  aspect_ratio_inferrer_image = local.aspect_ratio_inferrer_image
  feature_inferrer_image      = local.feature_inferrer_image

  inference_manager_image = local.inference_manager_image
  ingestor_images_image   = local.ingestor_images_image
  ingestor_works_image    = local.ingestor_works_image
  merger_image            = local.merger_image
  palette_inferrer_image  = local.palette_inferrer_image

  es_works_denormalised_index = local.es_works_denormalised_index
  es_works_merged_index       = local.es_works_merged_index
  es_images_augmented_index   = local.es_images_augmented_index
  es_images_index             = local.es_images_index
  es_images_initial_index     = local.es_images_initial_index
  es_works_identified_index   = local.es_works_identified_index
  es_works_index              = local.es_works_index

  cluster_name                        = aws_ecs_cluster.cluster.name
  scale_down_adjustment               = local.scale_down_adjustment
  scale_up_adjustment                 = local.scale_up_adjustment
  elasticsearch_users                 = null_resource.elasticsearch_users
  namespace_hyphen                    = local.namespace_hyphen
  pipeline_storage_es_service_secrets = local.pipeline_storage_es_service_secrets
  service_egress_security_group_id    = aws_security_group.service_egress.id


  inference_capacity_provider_name = module.inference_capacity_provider.name

  pipeline_date                 = var.pipeline_date
  pipeline_storage_port         = local.pipeline_storage_port
  pipeline_storage_private_host = local.pipeline_storage_private_host
  pipeline_storage_protocol     = local.pipeline_storage_protocol
  inferrer_lsh_model_key_value  = data.aws_ssm_parameter.inferrer_lsh_model_key.value
}
