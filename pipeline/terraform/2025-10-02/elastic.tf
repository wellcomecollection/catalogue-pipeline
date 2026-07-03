# The elastic module was moved from pipeline/terraform/modules/pipeline/elastic.tf
# to this file so the cluster lifecycle is separate from the pipeline service lifecycle.
# New pipeline stacks will use elastic_indices/ instead, pointing at the shared cluster
# in infrastructure/critical.
#
# Remote states are duplicated here from the pipeline module — this is intentional
# and temporary (only needed until 2025-10-02 is decommissioned).

data "terraform_remote_state" "shared_infra" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/platform-infrastructure/shared.tfstate"
    region = "eu-west-1"
  }
}

data "terraform_remote_state" "accounts_catalogue" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/aws-account-infrastructure/catalogue.tfstate"
    region = "eu-west-1"
  }
}

data "terraform_remote_state" "monitoring" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/monitoring.tfstate"
    region = "eu-west-1"
  }
}

locals {
  elastic_network_config = {
    vpc_id  = data.terraform_remote_state.accounts_catalogue.outputs["catalogue_vpc_delta_id"]
    subnets = data.terraform_remote_state.accounts_catalogue.outputs["catalogue_vpc_delta_private_subnets"]

    ec_privatelink_security_group_id = data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_sg_id"]

    traffic_filters = [
      data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_traffic_filter_id"],
      data.terraform_remote_state.shared_infra.outputs["ec_catalogue_privatelink_traffic_filter_id"],
      data.terraform_remote_state.shared_infra.outputs["ec_public_internet_traffic_filter_id"],
    ]
  }

  elastic_monitoring_config = {
    shared_logging_secrets       = data.terraform_remote_state.shared_infra.outputs["shared_secrets_logging"]
    logging_cluster_id           = data.terraform_remote_state.shared_infra.outputs["logging_cluster_id"]
    dlq_alarm_arn                = null
    main_q_age_alarm_action_arns = [data.terraform_remote_state.monitoring.outputs["chatbot_topic_arn"]]
  }

  # scale_up_elastic_cluster = false for this stack
  elastic_es_memory     = "4g"
  elastic_es_node_count = 3

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
}

module "elastic" {
  source = "../modules/pipeline/elastic"

  pipeline_date                  = local.pipeline_date
  es_cluster_deployment_template = "aws-cpu-optimized-arm"
  es_node_count                  = local.elastic_es_node_count
  es_memory                      = local.elastic_es_memory
  network_config                 = local.elastic_network_config
  monitoring_config              = local.elastic_monitoring_config
  allow_delete_indices           = false
  index_config                   = local.index_config
  catalogue_account_services     = ["catalogue_api", "snapshot_generator", "concepts_api"]
  version_regex                  = "9.1.?"

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
