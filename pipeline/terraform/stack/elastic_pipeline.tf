data "ec_deployment" "logging" {
  id = local.logging_cluster_id
}

locals {
  es_memory = var.is_reindexing ? "58g" : "15g"

  # When we're reindexing, this cluster isn't depended on for anything.
  # It's ephemeral data (and at 58GB of memory, expensive).
  #
  # Once we stop reindexing and make the pipeline live, we want it to be
  # highly available, to avoid issues with cross-cluster replication.
  es_node_count = var.is_reindexing ? 1 : 2
}

resource "ec_deployment" "pipeline" {
  name = "pipeline-${var.pipeline_date}"

  # Currently we do cross-cluster replication from the pipeline cluster
  # to the API cluster.
  #
  # The Elasticsearch documentation is very clear [1]
  #
  #     The cluster containing follower indices must be running the same
  #     or newer version of Elasticsearch as the remote cluster
  #
  # This even applies across patch versions, e.g. this configuration
  # is no good:
  #
  #     pipeline = v7.14.1
  #     api      = v7.14.0
  #
  # Using the same version as the API cluster means we'll never get CCR
  # versioning in a muddle.  If you want a newer version, upgrade the
  # API first.
  #
  # [1]: https://www.elastic.co/guide/en/elasticsearch/reference/current/xpack-ccr.html
  #
  version = var.api_ec_version

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

  for_each = toset(["snapshot_generator"])

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_username"

  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "es_password_catalogue" {
  provider = aws.catalogue

  for_each = toset(["snapshot_generator"])

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
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.0.1"

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
}

module "pipeline_storage_secrets_catalogue" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.0.1"

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
