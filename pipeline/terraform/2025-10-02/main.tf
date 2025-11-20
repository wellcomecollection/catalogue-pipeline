module "pipeline" {
  source = "../modules/pipeline"

  reindexing_state = {
    listen_to_reindexer      = true
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_matcher_db      = false
  }

  index_config = {
    (local.pipeline_date) = {
      works = {
        source       = "works_source.2025-10-02"
        identified   = "works_identified.2023-05-26"
        denormalised = "works_denormalised.2025-08-14"
        indexed      = "works_indexed.2024-11-14"
      }
      images = {
        initial   = "empty"
        augmented = "empty"
        indexed   = "images_indexed.2024-11-14"
      }
    }
    "2025-10-09" = {
      works = {
        indexed      = "works_indexed.2024-11-14"
        denormalised = "works_denormalised.2025-08-14"
      }
      images = {
        initial = "empty"
      }
      concepts = { indexed = "concepts_indexed.2025-06-17" }
    }
    "2025-11-20" = {
      works = {
        indexed = "works_indexed.2024-11-14"
      }
    }
  }

  allow_delete_indices = false

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  version_regex = "9.1.?"

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
