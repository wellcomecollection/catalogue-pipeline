module "pipeline" {
  source = "../modules/stack"

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
      indexed    = "works_indexed.2024-11-06"
    }
    images = {
      indexed        = "images_indexed.2024-11-06"
      works_analysis = "works_indexed.2024-11-06"
    }
  }

  allow_delete_indices = false

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  providers = {
    aws.catalogue = aws.catalogue
  }
}
