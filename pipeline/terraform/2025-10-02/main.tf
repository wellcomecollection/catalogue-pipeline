module "pipeline" {
  source = "../modules/pipeline"

  reindexing_state = {
    listen_to_reindexer      = true
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_matcher_db      = false
  }

  # Default values for a new pipeline
  # graph_index_dates = {
  #   merged   = local.pipeline_date
  #   works    = local.pipeline_date
  #   concepts = local.pipeline_date
  # }

  graph_index_dates = {
    merged   = "2025-10-02"
    works    = "2025-11-20"
    concepts = "2025-10-09"
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
    },
    "2025-11-20" = {
      works = {
        indexed = "works_indexed.2024-11-14"
      }
    }
    "2026-01-12" = {
      works = {
        source = "works_source.2025-10-02"
      }
    }
  }

  allow_delete_indices = false

  # Base AMI for ECS instances
  ami_id = "ami-0f8e471d509860e85"

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  version_regex = "9.1.?"

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
