module "pipeline" {
  source = "../modules/stack"

  es_cluster_memory              = "4g"
  es_cluster_deployment_template = "aws-cpu-optimized-arm"

  reindexing_state = {
    listen_to_reindexer      = true
    scale_up_tasks           = true
    scale_up_elastic_cluster = true
    scale_up_id_minter_db    = true
    scale_up_matcher_db      = true
  }

  index_config = {
    works = {
      identified = "works_identified.2023-05-26"
      merged     = "works_merged.2023-05-26"
      indexed    = "works_indexed.2023-11-09"
    }
    images = {
      indexed        = "images_indexed.2023-11-09"
      works_analysis = "works_indexed.2023-11-09"
    }
  }

  pipeline_date        = local.pipeline_date
  release_label        = local.pipeline_date
  allow_delete_indices = true

  providers = {
    aws.catalogue = aws.catalogue
  }
}
