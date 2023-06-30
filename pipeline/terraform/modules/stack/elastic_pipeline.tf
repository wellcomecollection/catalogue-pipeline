data "ec_stack" "latest_patch" {
  version_regex = "8.5.?"
  region        = "eu-west-1"
}

resource "ec_deployment" "pipeline" {
  name = "pipeline-${var.pipeline_date}"

  version = data.ec_stack.latest_patch.version

  region                 = "eu-west-1"
  deployment_template_id = "aws-io-optimized-v2"

  traffic_filter = var.network_config.traffic_filters

  elasticsearch {
    topology {
      id         = "hot_content"
      zone_count = local.es_node_count
      size       = local.es_memory
    }
    #    config {
    #      user_settings_yaml = "action.auto_create_index=false"
    #    }
  }

  kibana {
    topology {
      zone_count = 1
      size       = "1g"
    }
  }

  observability {
    deployment_id = var.monitoring_config.logging_cluster_id
  }

  lifecycle {
    ignore_changes = [

      # We've had issues in the past when an Elastic upgrade is triggered
      # unexpectedly, sometimes causing missing master nodes/search issues.
      # e.g. https://wellcome.slack.com/archives/C01FBFSDLUA/p1663849437942949
      #
      # This `ignore_changes` means Terraform won't upgrade the cluster
      # version after initial creation -- we'll have to log in to the
      # Elastic Cloud console and trigger it explicitly.
      version,
    ]
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
    "concepts_api",
  ])

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/es_username"

  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret" "es_password_catalogue" {
  provider = aws.catalogue

  for_each = toset([
    "snapshot_generator",
    "catalogue_api",
    "concepts_api",
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

module "pipeline_indices" {

  source = "../pipeline_indices"

  es_works_source_index       = local.es_works_source_index
  es_works_merged_index       = local.es_works_merged_index
  es_works_identified_index   = local.es_works_identified_index
  es_works_denormalised_index = local.es_works_denormalised_index
  es_works_index              = local.es_works_index

  es_images_initial_index   = local.es_images_initial_index
  es_images_augmented_index = local.es_images_augmented_index
  es_images_index           = local.es_images_index

  # Path to folder containing mappings and analysis settings for Elasticsearch Index creation
  es_config_path = "${path.root}/../../index_config"
  connection = {
    username  = ec_deployment.pipeline.elasticsearch_username
    password  = ec_deployment.pipeline.elasticsearch_password
    endpoints = [ec_deployment.pipeline.elasticsearch[0].https_endpoint]
  }

}
