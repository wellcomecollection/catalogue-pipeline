module "graph_pipeline" {
  source = "./graph_pipeline"

  pipeline_date = var.pipeline_date
  index_dates = {
    works    = "2025-10-09"
    concepts = "2025-10-09"
  }

  es_cluster_host     = module.elastic.pipeline_storage_private_host
  es_cluster_port     = module.elastic.pipeline_storage_port
  es_cluster_protocol = module.elastic.pipeline_storage_protocol

  es_secrets = {
    concepts_ingestor = module.elastic.pipeline_storage_es_service_secrets["concepts_ingestor"]["es_apikey"],
    works_ingestor    = module.elastic.pipeline_storage_es_service_secrets["works_ingestor"]["es_apikey"],
    graph_extractor   = module.elastic.pipeline_storage_es_service_secrets["graph_extractor"]["es_apikey"],
  }
}


