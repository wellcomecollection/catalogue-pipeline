# This pipeline uses the shared ES cluster from infrastructure/critical
# rather than creating its own. The elastic_indices module manages only
# indices, API keys, and ingest pipelines against the existing cluster.

data "terraform_remote_state" "catalogue_infra_critical" {
  backend = "s3"

  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/catalogue/infrastructure/critical.tfstate"
    region = "eu-west-1"
  }
}

locals {
  es_cluster = data.terraform_remote_state.catalogue_infra_critical.outputs.pipeline_storage_es_cluster_v1

  index_config = {
    (local.pipeline_date) = {
      works = {
        // transformers - id_minter
        source = "works_source.2026-03-25"
        // id_minter - matcher_merger
        identified = "works_identified.2023-05-26"
        // matcher_merger - graph/ingestor/indexer
        denormalised = "works_denormalised.2025-08-14"
        // graph/ingestor/indexer - API
        indexed = "works_indexed.2024-11-14"
      }
      images = {
        // matcher_merger - images_inferrer
        initial   = "images_initial.2026-06-15"
        // images_inferrer - graph/ingestor/indexer
        augmented = "images_augmented.2026-04-29"
        // graph/ingestor/indexer - API
        indexed = "images_indexed.2024-11-14"
      }
      concepts = {
        // graph/ingestor/indexer - API
        indexed = "concepts_indexed.2025-06-17"
      }
    }
  }
}

module "elastic" {
  source = "../modules/pipeline/elastic_indices"

  pipeline_date              = local.pipeline_date
  es_cluster_date            = local.es_cluster_date
  es_endpoint                = local.es_cluster.https_endpoint
  es_username                = local.es_cluster.username
  es_password                = local.es_cluster.password
  es_private_host            = local.es_cluster.private_host
  es_port                    = local.es_cluster.port
  es_protocol                = local.es_cluster.protocol
  allow_delete_indices       = false
  index_config               = local.index_config
  catalogue_account_services = ["catalogue_api", "snapshot_generator", "concepts_api"]

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
