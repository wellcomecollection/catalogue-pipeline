module "pipeline" {
  source = "../modules/stack"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

  index_config = {
    works = {
      identified   = "works_identified.2023-05-26"
      denormalised = "works_denormalised.2023-05-26"
      indexed      = "works_indexed.2024-11-14"
    }
    images = {
      indexed        = "images_indexed.2024-11-14"
      works_analysis = "works_indexed.2024-11-06"
    }
    concepts = {
      # Define a set of concept indexes, each with its own config definition
      indexed = {
        "2025-07-09" = "concepts_indexed.2025-06-17"
        "2025-07-21" = "concepts_indexed.2025-06-17"
        "2025-08-13" = "concepts_indexed.2025-06-17"
      }
    }
  }

  allow_delete_indices = false

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  providers = {
    aws.catalogue = aws.catalogue
  }
}
