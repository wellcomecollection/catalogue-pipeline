data "ec_stack" "latest_patch" {
  version_regex = "8.10.?"
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
}

locals {
  indices                   = module.pipeline_indices.index_names
  service_index_permissions = {
    transformer = {
      read  = []
      write = [local.indices.source]
    }
    id_minter = {
      read  = [local.indices.source]
      write = [local.indices.works_identified]
    }
    matcher = {
      read  = [local.indices.works_identified]
      write = []
    }
    merger = {
      read  = [local.indices.works_identified]
      write = [local.indices.works_merged, local.indices.images_initial]
    }
    router = {
      read  = [local.indices.works_merged]
      write = [local.indices.denormalised]
    }
    path_concatenator = {
      read  = [local.indices.works_merged]
      write = [local.indices.works_merged]
    }
    relation_embedder = {
      read  = [local.indices.works_merged]
      write = [local.indices.denormalised]
    }
    work_ingestor = {
      read  = [local.indices.denormalised]
      write = [local.indices.works_indexed]
    }
    inferrer = {
      read  = [local.indices.images_initial]
      write = [local.indices.images_augmented]
    }
    image_ingestor = {
      read  = [local.indices.images_augmented]
      write = [local.indices.images_indexed]
    }
    snapshot_generator = {
      read  = [local.indices.works_indexed, local.indices.images_indexed]
      write = []
    }
    catalogue_api = {
      read  = [local.indices.works_indexed, local.indices.images_indexed]
      write = []
    }
  }

  pipeline_storage_es_service_secrets = {
    for service in keys(local.service_index_permissions) : service => {
      es_host     = local.pipeline_storage_private_host
      es_port     = local.pipeline_storage_port
      es_protocol = local.pipeline_storage_protocol
      es_apikey   = "elasticsearch/pipeline_storage_${var.pipeline_date}/${service}/api_key"
    }
  }

  catalogue_account_services = toset([
    "catalogue_api",
    "snapshot_generator"
  ])
}


module "pipeline_services" {
  source    = "../pipeline_es_api_key"
  for_each  = local.service_index_permissions
  name      = each.key
  read_from = each.value.read
  write_to  = each.value.write

  pipeline_date       = var.pipeline_date
  expose_to_catalogue = contains(local.catalogue_account_services, each.key)
  providers           = {
    aws.catalogue = aws.catalogue
  }
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


resource "random_password" "read_only_user" {
  length = 16
}

resource "elasticstack_elasticsearch_security_user" "read_only" {
  username = "read_only"
  password = random_password.read_only_user.result
  roles    = [elasticstack_elasticsearch_security_role.read_only.name]
}

module "readonly_user_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"

  deletion_mode = "IMMEDIATE"
  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/read_only/es_username" = elasticstack_elasticsearch_security_user.read_only.username
    "elasticsearch/pipeline_storage_${var.pipeline_date}/read_only/es_password" = elasticstack_elasticsearch_security_user.read_only.password
  }
}
