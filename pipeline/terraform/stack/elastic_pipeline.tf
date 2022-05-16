data "ec_deployment" "logging" {
  id = local.logging_cluster_id
}

locals {
  es_memory = var.reindexing_state.scale_up_elastic_cluster ? "58g" : "8g"

  # When we're reindexing, this cluster isn't depended on for anything.
  # It's ephemeral data (and at 58GB of memory, expensive).
  #
  # Once we stop reindexing and make the pipeline live, we want it to be
  # highly available, to avoid issues with cross-cluster replication.
  es_node_count = var.reindexing_state.scale_up_elastic_cluster ? 1 : 2
}

data "ec_stack" "latest_patch" {
  version_regex = "7.17.?"
  region        = "eu-west-1"
}

resource "ec_deployment" "pipeline" {
  name = "pipeline-${var.pipeline_date}"

  version = data.ec_stack.latest_patch.version

  region                 = "eu-west-1"
  deployment_template_id = "aws-io-optimized-v2"

  traffic_filter = [
    var.traffic_filter_platform_vpce_id,
    var.traffic_filter_catalogue_vpce_id,
    var.traffic_filter_public_internet_id,
  ]

  elasticsearch {
    topology {
      id         = "hot_content"
      zone_count = local.es_node_count
      size       = local.es_memory
    }
  }

  kibana {
    topology {
      zone_count = 1
      size       = "1g"
    }
  }

  # TODO: Why do we round-trip this via a data block?
  observability {
    deployment_id = data.ec_deployment.logging.id
  }
}

# We create the username/password secrets in Terraform, and set the values in the
# Python script.  This ensures the secrets will be properly cleaned up when we delete
# a pipeline.
#
# We set the recovery window to 0 so that secrets are deleted immediately,
# rather than hanging around for a 30 day recovery period.
# See https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/secretsmanager_secret#recovery_window_in_days
#
resource "aws_secretsmanager_secret" "es_username" {
  for_each = toset(concat(local.pipeline_storage_service_list, ["image_ingestor", "work_ingestor", "read_only"]))

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_username"

  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "es_password" {
  for_each = toset(concat(local.pipeline_storage_service_list, ["image_ingestor", "work_ingestor", "read_only"]))

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_password"

  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "es_username_catalogue" {
  provider = aws.catalogue

  for_each = toset([
    "snapshot_generator",
    "catalogue_api",
  ])

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_username"

  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "es_password_catalogue" {
  provider = aws.catalogue

  for_each = toset([
    "snapshot_generator",
    "catalogue_api",
  ])

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_password"

  recovery_window_in_days = 0
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
  pipeline_storage_elastic_id     = ec_deployment.pipeline.elasticsearch[0].resource_id
  pipeline_storage_elastic_region = ec_deployment.pipeline.elasticsearch[0].region

  pipeline_storage_public_host  = "elasticsearch/pipeline_storage_${var.pipeline_date}/public_host"
  pipeline_storage_private_host = "elasticsearch/pipeline_storage_${var.pipeline_date}/private_host"
  pipeline_storage_protocol     = "elasticsearch/pipeline_storage_${var.pipeline_date}/protocol"
  pipeline_storage_port         = "elasticsearch/pipeline_storage_${var.pipeline_date}/port"
  pipeline_storage_es_username  = "elasticsearch/pipeline_storage_${var.pipeline_date}/es_username"
  pipeline_storage_es_password  = "elasticsearch/pipeline_storage_${var.pipeline_date}/es_password"
}

module "pipeline_storage_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.3.0"

  key_value_map = {
    (local.pipeline_storage_public_host) = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    (local.pipeline_storage_es_username) = ec_deployment.pipeline.elasticsearch_username
    (local.pipeline_storage_es_password) = ec_deployment.pipeline.elasticsearch_password

    # The endpoint value is of the form https://{deployment_id}.eu-west-1.aws.found.io:9243
    # We could hard-code these values as "https" and "9243", but we can just as easily infer
    # them from the actual endpoint value.
    (local.pipeline_storage_protocol) = split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint)[0]
    (local.pipeline_storage_port)     = reverse(split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint))[0]

    # See https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
    (local.pipeline_storage_private_host) = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }

  deletion_mode = "IMMEDIATE"
}

module "pipeline_storage_secrets_catalogue" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.3.0"

  providers = {
    aws = aws.catalogue
  }

  key_value_map = {
    (local.pipeline_storage_public_host) = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    (local.pipeline_storage_es_username) = ec_deployment.pipeline.elasticsearch_username
    (local.pipeline_storage_es_password) = ec_deployment.pipeline.elasticsearch_password

    # The endpoint value is of the form https://{deployment_id}.eu-west-1.aws.found.io:9243
    # We could hard-code these values as "https" and "9243", but we can just as easily infer
    # them from the actual endpoint value.
    (local.pipeline_storage_protocol) = split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint)[0]
    (local.pipeline_storage_port)     = reverse(split(":", ec_deployment.pipeline.elasticsearch[0].https_endpoint))[0]

    # See https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
    (local.pipeline_storage_private_host) = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }

  deletion_mode = "IMMEDIATE"
}

locals {
  pipeline_storage_service_list = [
    "id_minter",
    "matcher",
    "merger",
    "transformer",
    "path_concatenator",
    "relation_embedder",
    "router",
    "inferrer",
    "work_ingestor",
    "image_ingestor",
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
