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
    merged    = "2025-10-02"
    augmented = "2026-06-15"
    works     = "2026-03-03"
    concepts  = "2026-03-03"
    images    = "2026-04-29"
  }

  index_config = {
    (local.pipeline_date) = {
      works = {
        // prod transformers - prod id_minter
        source = "works_source.2026-03-25"
        // prod id_minter - prod matcher_merger
        identified = "works_identified.2023-05-26"
        // prod matcher_merger - prod graph/ingestor/indexer
        denormalised = "works_denormalised.2025-08-14"
      }
      images = {
        // prod matcher_merger - OLD SQS-driven inference manager.
        // POST-CUTOVER: orphaned. The merger + Scala inferrer now write/read the
        // modifiedTime-mapped images-initial-2026-06-15 (see the 2026-06-15 entry below),
        // so nothing writes this index any more. Remove this `initial` entry to delete the
        // old index once we're confident the new path is the source of truth.
        initial = "empty"
        // scala images ingestor - to be deleted when the service is removed
        augmented = "empty"
        // scala images ingestor - to be deleted when the service is removed
        indexed = "images_indexed.2024-11-14"
      }
    }
    "2025-10-09" = {
      works = {
        // test matcher_merger - WCSTP dev
        denormalised = "works_denormalised.2025-08-14"
      }
      images = {
        // test matcher_merger - WCSTP dev
        initial = "empty"
      }
    },
    "2026-01-12" = {
      works = {
        // test transformers - WCSTP dev
        source = "works_source.2026-03-25"
      }
    },
    "2026-03-03" = {
      works = {
        // prod graph/ingestor/indexer - prod API
        indexed = "works_indexed.2024-11-14"
      }
      concepts = {
        // prod graph/ingestor/indexer - prod API
        indexed = "concepts_indexed.2025-06-17"
      }
    },
    "2026-03-06" = {
      works = {
        // test id_minter - test matcher_merger - WCSTP dev
        identified = "works_identified.2023-05-26"
      }
    },
    "2026-04-29" = {
      images = {
        // Old Scala inference manager output. The Scala service has been retired, so nothing writes this
        // index now, and the graph read-path reads images-augmented-2026-06-15 (graph_index_dates.augmented).
        // This index is orphaned; terraform still manages it under deletion_protection, so dropping it is a
        // separate operational step. Remove this augmented entry once the index has been deleted.
        augmented = "images_augmented.2026-04-29"
        // prod graph/ingestor/indexer - prod API
        indexed = "images_indexed.2024-11-14"
      }
    }
    // Indexes for the new Python image-inferrer state machine.
    // (Date-only name, chosen when these started as shadow indexes.)
    //  - initial: now the PRODUCTION source index. The merger and both inferrers
    //    write/read this; it has an explicit mapping that indexes modifiedTime, which
    //    the find_work time-window query needs (the old images-initial-2025-10-02 used
    //    the "empty"/dynamic:false mapping, where modifiedTime is unqueryable). No longer
    //    a shadow index — keep it. (Will be a normal images-initial-<date> on the next
    //    full pipeline reindex; the off-pipeline-date name is cosmetic until then.)
    //  - augmented: the scheduled inferrer's output (reuses the images_augmented.2026-04-29 mapping).
    //    This is the production augmented index feeding the API: both the inferrer's write target and
    //    the graph read-path source, resolved from graph_index_dates.augmented = "2026-06-15".
    "2026-06-15" = {
      images = {
        initial   = "images_initial.2026-06-15"
        augmented = "images_augmented.2026-04-29"
      }
    }
  }

  allow_delete_indices = false

  # Image-inferrer. The scheduled Python inferrer is the sole inferrer: it reads images-initial-2026-06-15
  # and writes images-augmented-2026-06-15, which the graph read-path also reads.
  # graph_index_dates.augmented = "2026-06-15" is the single source for both. image_inferrer_initial_index_date
  # is overridden because it otherwise falls back to pipeline_date.
  image_inferrer_initial_index_date = "2026-06-15"

  # Base AMI for ECS instances
  ami_id = "resolve:ssm:arn:aws:ssm:eu-west-1:760097843905:parameter/imagebuilder/weco-al2023-ecs-optimised-x86_64/latest"

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  version_regex = "9.1.?"

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
