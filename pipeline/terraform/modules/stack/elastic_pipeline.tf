data "ec_stack" "latest_patch" {
  version_regex = "8.5.?"
  region        = "eu-west-1"
}

resource "ec_deployment" "pipeline" {
  name = "pipeline-${var.pipeline_date}"

  version = data.ec_stack.latest_patch.version

  region                 = "eu-west-1"
  deployment_template_id = "aws-io-optimized-v2"

  traffic_filter = local.network_config.traffic_filters

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
    deployment_id = local.monitoring_config.logging_cluster_id
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

  index_config = var.index_config

  # Path to folder containing mappings and analysis settings for Elasticsearch Index creation
  es_config_path = "${path.root}/../../../index_config"
  connection = {
    username  = ec_deployment.pipeline.elasticsearch_username
    password  = ec_deployment.pipeline.elasticsearch_password
    endpoints = [ec_deployment.pipeline.elasticsearch[0].https_endpoint]
  }

}

locals {
  indices = module.pipeline_indices.indices
  service_index_role_descriptors = {
    transformer        = [local.indices.source.write]
    id_minter          = [local.indices.source.write, local.indices.works_identified.write]
    matcher            = [local.indices.works_identified.read]
    merger             = [local.indices.works_identified.read, local.indices.works_merged.write, local.indices.images_initial.write]
    router             = [local.indices.works_merged.read, local.indices.denormalised.write]
    path_concatenator  = [local.indices.works_merged.read, local.indices.works_merged.write]
    relation_embedder  = [local.indices.works_merged.read, local.indices.denormalised.write]
    work_ingestor      = [local.indices.denormalised.read, local.indices.works_indexed.write]
    inferrer           = [local.indices.images_initial.read, local.indices.images_augmented.write]
    image_ingestor     = [local.indices.images_augmented.read, local.indices.images_indexed.write]
    snapshot_generator = [local.indices.works_indexed.read, local.indices.images_indexed.read]
    catalogue_api      = [local.indices.works_indexed.read, local.indices.images_indexed.read]
  }
}

resource "elasticstack_elasticsearch_security_api_key" "pipeline_services" {
  for_each = local.service_index_role_descriptors

  name             = "${each.key}-${var.pipeline_date}"
  role_descriptors = jsonencode(merge(each.value...))
}

resource "aws_secretsmanager_secret" "pipeline_services" {
  for_each = elasticstack_elasticsearch_security_api_key.pipeline_services

  name = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/api_key"
}

resource "aws_secretsmanager_secret_version" "pipeline_services" {
  for_each = elasticstack_elasticsearch_security_api_key.pipeline_services

  secret_id = "elasticsearch/pipeline_storage_${var.pipeline_date}/${each.key}/api_key"
  # More than just the API key, as used by services
  # "API key credentials which is the Base64-encoding of the UTF-8 representation of the id and api_key joined by a colon"
  # https://registry.terraform.io/providers/elastic/elasticstack/latest/docs/resources/elasticsearch_security_api_key#read-only
  # https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-create-api-key.html#security-api-create-api-key-example
  secret_string = each.value.encoded
}

# This role isn't used by applications, but instead provided to give developer scripts
# read-only access to the pipeline_storage cluster.
resource "elasticstack_elasticsearch_security_role" "read_only" {
  name = "read_only"

  indices {
    names      = [for idx in module.pipeline_indices.indices : idx.name]
    privileges = ["read"]
  }
}

resource "elasticstack_elasticsearch_security_user" "read_only" {
  username = "read_only"
  roles    = [elasticstack_elasticsearch_security_role.read_only.name]
}
