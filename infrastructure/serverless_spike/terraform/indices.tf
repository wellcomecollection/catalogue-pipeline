# Phase 2: the real indices, ingest pipeline, service keys and secrets, so the
# pipeline code connects to the project with no other change.
module "elastic" {
  source = "../../../pipeline/terraform/modules/pipeline/elastic_indices"

  pipeline_date              = local.pipeline_date
  es_cluster                 = module.project.es_cluster
  allow_delete_indices       = true
  index_config               = local.index_config
  catalogue_account_services = ["catalogue_api"]

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}

# The harness writes to the spike's own indices and nothing else.
resource "elasticstack_elasticsearch_security_api_key" "serverless_bench" {
  provider = elasticstack.serverless

  name = "es-bench-${local.pipeline_date}"

  role_descriptors = jsonencode({
    bench = {
      indices = [
        {
          names                    = ["*-${local.pipeline_date}"]
          privileges               = ["all"]
          allow_restricted_indices = false
        }
      ]
    }
  })

  depends_on = [module.elastic]
}

module "serverless_bench_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.5.0"

  deletion_mode = "IMMEDIATE"

  key_value_map = {
    "elasticsearch/pipeline_storage_${local.pipeline_date}/bench/api_key" = elasticstack_elasticsearch_security_api_key.serverless_bench.encoded
  }
}
