module "graph_pipeline" {
  source = "./graph"

  pipeline_date = var.pipeline_date
  index_dates = {
    merged   = "2025-10-02"
    works    = "2025-11-20"
    concepts = "2025-10-09"
  }

  ecs_cluster_arn = aws_ecs_cluster.cluster.arn

  es_cluster_host     = module.elastic.pipeline_storage_private_host
  es_cluster_port     = module.elastic.pipeline_storage_port
  es_cluster_protocol = module.elastic.pipeline_storage_protocol

  es_secrets = {
    concepts_ingestor = module.elastic.pipeline_storage_es_service_secrets["concepts_ingestor"]["es_apikey"],
    works_ingestor    = module.elastic.pipeline_storage_es_service_secrets["works_ingestor"]["es_apikey"],
    graph_extractor   = module.elastic.pipeline_storage_es_service_secrets["graph_extractor"]["es_apikey"],
  }
}
