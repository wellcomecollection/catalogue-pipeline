locals {
  es_memory = var.is_reindexing ? "29g" : "8g"
}

resource "ec_deployment" "pipeline_storage" {
  name = "catalogue-pipeline-storage-${var.pipeline_date}"

  region                 = "eu-west-1"
  version                = "7.10.2"
  deployment_template_id = "aws-io-optimized-v2"

  traffic_filter = [
    var.traffic_filter_platform_vpce_id,
    var.traffic_filter_public_internet_id,
  ]

  elasticsearch {
    topology {
      zone_count = 1
      size       = local.es_memory
    }
  }

  kibana {
    topology {
      zone_count = 1
      size       = "1g"
    }
  }
}

# We create the username/password secrets in Terraform, and set the values in the
# Python script.  This ensures the secrets will be properly cleaned up when we delete
# a pipeline.
resource "aws_secretsmanager_secret" "es_username" {
  for_each = toset(concat(local.pipeline_storage_service_list, ["image_ingestor", "work_ingestor", "read_only"]))

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_username"
}

resource "aws_secretsmanager_secret" "es_password" {
  for_each = toset(concat(local.pipeline_storage_service_list, ["image_ingestor", "work_ingestor", "read_only"]))

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_password"
}

# We can't attach the provisioner directly to the Elastic Cloud resource (I'm not
# entirely sure why), so instead we create a null resource that will be recreated
# whenever the cluster is created.
#
# The local-exec provisioner on this resource runs a script that sets up the
# Elasticsearch users for the pipeline.
#
# Note: this must run *after* the cluster and secrets are created, or the script
# won't work.
#
# TODO: Investigate why we can't attach the provisioner directly to the ec_deployment resource.
# Some informal testing with a minimal TF configuration that just uses the EC provider
# shows that this *should* work.
resource "null_resource" "elasticsearch_users" {
  triggers = {
    pipeline_storage_elastic_id = local.pipeline_storage_elastic_id
  }

  depends_on = [
    module.pipeline_storage_secrets,
    aws_secretsmanager_secret.es_username,
    aws_secretsmanager_secret.es_password,
  ]

  provisioner "local-exec" {
    command = "python3 scripts/create_pipeline_storage_users.py ${var.pipeline_date}"
  }
}

locals {
  pipeline_storage_elastic_id     = ec_deployment.pipeline_storage.elasticsearch[0].resource_id
  pipeline_storage_elastic_region = ec_deployment.pipeline_storage.elasticsearch[0].region

  pipeline_storage_public_host  = "elasticsearch/pipeline_storage_${var.pipeline_date}/public_host"
  pipeline_storage_private_host = "elasticsearch/pipeline_storage_${var.pipeline_date}/private_host"
  pipeline_storage_protocol     = "elasticsearch/pipeline_storage_${var.pipeline_date}/protocol"
  pipeline_storage_port         = "elasticsearch/pipeline_storage_${var.pipeline_date}/port"
  pipeline_storage_es_username  = "elasticsearch/pipeline_storage_${var.pipeline_date}/es_username"
  pipeline_storage_es_password  = "elasticsearch/pipeline_storage_${var.pipeline_date}/es_password"
}

module "pipeline_storage_secrets" {
  source = "../../../infrastructure/modules/secrets"

  key_value_map = {
    (local.pipeline_storage_public_host) = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    (local.pipeline_storage_es_username) = ec_deployment.pipeline_storage.elasticsearch_username
    (local.pipeline_storage_es_password) = ec_deployment.pipeline_storage.elasticsearch_password

    # The endpoint value is of the form https://{deployment_id}.eu-west-1.aws.found.io:9243
    # We could hard-code these values as "https" and "9243", but we can just as easily infer
    # them from the actual endpoint value.
    (local.pipeline_storage_protocol) = split(":", ec_deployment.pipeline_storage.elasticsearch[0].https_endpoint)[0]
    (local.pipeline_storage_port)     = reverse(split(":", ec_deployment.pipeline_storage.elasticsearch[0].https_endpoint))[0]

    # See https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
    (local.pipeline_storage_private_host) = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }
}

locals {
  pipeline_storage_service_list = [
    "id_minter",
    "matcher",
    "merger",
    "transformer",
    "relation_embedder",
    "router",
    "inferrer",
  ]

  pipeline_storage_es_service_secrets = zipmap(local.pipeline_storage_service_list, [
    for service in local.pipeline_storage_service_list :
    {
      es_host     = local.pipeline_storage_private_host
      es_port     = local.pipeline_storage_port
      es_protocol = local.pipeline_storage_protocol
      es_username = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/es_username"
      es_password = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/es_password"
    }
  ])
}
