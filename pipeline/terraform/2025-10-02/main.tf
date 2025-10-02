module "pipeline" {
  source = "../modules/pipeline"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

  index_config = {
    works = {
      identified   = {
        local.pipeline_date = "works_identified.2023-05-26"
      }
      denormalised = {
        local.pipeline_date = "works_denormalised.2025-08-14"
      }
      indexed      = {
        local.pipeline_date = "works_indexed.2024-11-14"
      }
      source       = {
        local.pipeline_date = "works_source.2025-10-02"
      }
    }
    images = {
      initial        = {
        local.pipeline_date = "empty"
      }
      augmented      = {
        local.pipeline_date = "empty"
      }
      indexed        = {
        local.pipeline_date = "images_indexed.2024-11-14"
      }
    }
    concepts = {
      indexed = {
        local.pipeline_date = "concepts_indexed.2025-06-17"
      }
    }
  }

  allow_delete_indices = true

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  providers = {
    aws.catalogue = aws.catalogue
  }
}
