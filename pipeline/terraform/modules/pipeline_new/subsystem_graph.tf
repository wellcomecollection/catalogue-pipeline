module "graph_pipeline" {
  source = "../pipeline_services/graph"

  pipeline_date = var.pipeline_date
  graph_date    = var.graph_date
  index_dates = {
    merged    = var.index_dates.merged
    augmented = var.index_dates.augmented
    works     = var.index_dates.works
    concepts  = var.index_dates.concepts
    images    = var.index_dates.images
  }

  ecs_cluster_arn = aws_ecs_cluster.cluster.arn

  es_cluster_host     = var.elastic.pipeline_storage_private_host
  es_cluster_port     = var.elastic.pipeline_storage_port
  es_cluster_protocol = var.elastic.pipeline_storage_protocol

  es_secrets = {
    concepts_ingestor = var.elastic.pipeline_storage_es_service_secrets["concepts_ingestor"]["es_apikey"],
    works_ingestor    = var.elastic.pipeline_storage_es_service_secrets["works_ingestor"]["es_apikey"],
    images_ingestor   = var.elastic.pipeline_storage_es_service_secrets["images_ingestor"]["es_apikey"],
    graph_extractor   = var.elastic.pipeline_storage_es_service_secrets["graph_extractor"]["es_apikey"],
  }
}
