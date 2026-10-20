locals {
  # Namespaces the indices, API keys and secrets like a dated pipeline would.
  pipeline_date = "2026-10-14-serverless"

  region_id = "aws-eu-west-1"

  catalogue_vpcs  = data.terraform_remote_state.accounts_catalogue.outputs
  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]

  hosted_cluster = data.terraform_remote_state.catalogue_infra_critical.outputs.es_cluster_2026_07_03

  # Lets the harness reach the hosted private endpoint too, for a like-for-like comparison.
  hosted_privatelink_sg_id = data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_sg_id"]

  es_config_path = "${path.root}/../../../index_config"

  # Same mappings as the 2026-09-30 pipeline, so the comparison is like for like.
  index_config = {
    (local.pipeline_date) = {
      works = {
        source       = "works_source.2026-03-25"
        identified   = "works_identified.2023-05-26"
        denormalised = "works_denormalised.2025-08-14"
        indexed      = "works_indexed.2026-07-03"
      }
      images = {
        initial   = "images_initial.2026-06-15"
        augmented = "images_augmented.2026-04-29"
        indexed   = "images_indexed.2024-11-14"
      }
      concepts = {
        indexed = "concepts_indexed.2025-06-17"
      }
    }
  }

  sample_source_indices = [
    "works-denormalised-2026-09-30",
    "works-source-2026-09-30",
  ]
}
