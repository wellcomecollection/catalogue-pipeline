module "graph_pipeline" {
  source = "./graph"

  pipeline_date = var.pipeline_date
  # The graph subsystem's augmented source can be decoupled from graph_index_dates.augmented (which
  # the old Scala inferrer still writes) via graph_images_augmented_index_date — see that variable and
  # local.graph_images_augmented_index_date. All other dates pass through unchanged.
  index_dates = {
    merged    = var.graph_index_dates.merged
    augmented = local.graph_images_augmented_index_date
    works     = var.graph_index_dates.works
    concepts  = var.graph_index_dates.concepts
    images    = var.graph_index_dates.images
  }

  ecs_cluster_arn = aws_ecs_cluster.cluster.arn

  es_cluster_host     = module.elastic.pipeline_storage_private_host
  es_cluster_port     = module.elastic.pipeline_storage_port
  es_cluster_protocol = module.elastic.pipeline_storage_protocol

  es_secrets = {
    concepts_ingestor = module.elastic.pipeline_storage_es_service_secrets["concepts_ingestor"]["es_apikey"],
    works_ingestor    = module.elastic.pipeline_storage_es_service_secrets["works_ingestor"]["es_apikey"],
    images_ingestor   = module.elastic.pipeline_storage_es_service_secrets["images_ingestor"]["es_apikey"],
    graph_extractor   = module.elastic.pipeline_storage_es_service_secrets["graph_extractor"]["es_apikey"],
  }
}
