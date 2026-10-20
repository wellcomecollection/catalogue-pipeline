# A comparator index on the hosted cluster with the same mapping, so the harness
# runs an identical matrix against both targets.
module "hosted_comparator_index" {
  source = "../../../pipeline/terraform/modules/es_index"

  name          = "works-denormalised-${local.pipeline_date}-hosted-comparator"
  mappings_name = "works_denormalised.2025-08-14"
  config_path   = local.es_config_path
  allow_delete  = true

  providers = {
    elasticstack = elasticstack.hosted
  }
}

# Writes only to the comparator; reads the 2026-09-30 indices for sampling.
resource "elasticstack_elasticsearch_security_api_key" "hosted_bench" {
  provider = elasticstack.hosted

  name = "es-bench-${local.pipeline_date}-hosted"

  role_descriptors = jsonencode({
    bench = {
      indices = [
        {
          names                    = [module.hosted_comparator_index.name]
          privileges               = ["all"]
          allow_restricted_indices = false
        },
        {
          names                    = local.sample_source_indices
          privileges               = ["read"]
          allow_restricted_indices = false
        }
      ]
    }
  })
}

module "hosted_bench_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.5.0"

  deletion_mode = "IMMEDIATE"

  key_value_map = {
    "elasticsearch/pipeline_storage_${local.pipeline_date}/bench_hosted/api_key"      = elasticstack_elasticsearch_security_api_key.hosted_bench.encoded
    "elasticsearch/pipeline_storage_${local.pipeline_date}/bench_hosted/public_host"  = local.hosted_cluster.public_host
    "elasticsearch/pipeline_storage_${local.pipeline_date}/bench_hosted/private_host" = local.hosted_cluster.private_host
    "elasticsearch/pipeline_storage_${local.pipeline_date}/bench_hosted/port"         = local.hosted_cluster.port
    "elasticsearch/pipeline_storage_${local.pipeline_date}/bench_hosted/protocol"     = local.hosted_cluster.protocol
  }
}
