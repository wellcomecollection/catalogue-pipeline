variable "es_cluster_deployment_template" {
  type    = string
  default = "aws-cpu-optimized-arm"
}

variable "es_node_count" {
  type    = number
  default = 3
}

variable "es_memory" {
  type    = string
  default = "4g"
}

variable "version_regex" {
  type    = string
  default = "9.1.?"
}

data "ec_stack" "latest_patch" {
  version_regex = var.version_regex
  region        = "eu-west-1"
}

resource "ec_deployment" "pipeline_storage" {
  name                   = "pipeline-storage"
  version                = data.ec_stack.latest_patch.version
  region                 = "eu-west-1"
  deployment_template_id = var.es_cluster_deployment_template
  traffic_filter = [
    data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_traffic_filter_id"],
    data.terraform_remote_state.shared_infra.outputs["ec_catalogue_privatelink_traffic_filter_id"],
    data.terraform_remote_state.shared_infra.outputs["ec_public_internet_traffic_filter_id"],
  ]

  elasticsearch = {
    hot = {
      size        = var.es_memory
      zone_count  = var.es_node_count
      autoscaling = {}
    }
  }

  kibana = {
    size       = "1g"
    zone_count = 1
  }

  observability = {
    deployment_id = data.terraform_remote_state.shared_infra.outputs["logging_cluster_id"]
  }

  lifecycle { ignore_changes = [version] }
}

provider "elasticstack" {
  elasticsearch {
    username  = ec_deployment.pipeline_storage.elasticsearch_username
    password  = ec_deployment.pipeline_storage.elasticsearch_password
    endpoints = [ec_deployment.pipeline_storage.elasticsearch.https_endpoint]
  }
}

locals {
  pipeline_storage_elastic_id     = ec_deployment.pipeline_storage.elasticsearch.resource_id
  pipeline_storage_elastic_region = ec_deployment.pipeline_storage.elasticsearch.region

  pipeline_storage_secrets_kv_map = {
    "elasticsearch/pipeline_storage_v1/public_host"  = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"
    "elasticsearch/pipeline_storage_v1/private_host" = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
    "elasticsearch/pipeline_storage_v1/protocol"     = split(":", ec_deployment.pipeline_storage.elasticsearch.https_endpoint)[0]
    "elasticsearch/pipeline_storage_v1/port"         = reverse(split(":", ec_deployment.pipeline_storage.elasticsearch.https_endpoint))[0]
    "elasticsearch/pipeline_storage_v1/es_username"  = ec_deployment.pipeline_storage.elasticsearch_username
    "elasticsearch/pipeline_storage_v1/es_password"  = ec_deployment.pipeline_storage.elasticsearch_password
  }
}

module "pipeline_storage_secrets" {
  source        = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"
  deletion_mode = "IMMEDIATE"
  key_value_map = local.pipeline_storage_secrets_kv_map
}

module "pipeline_storage_secrets_catalogue" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"
  providers = {
    aws = aws.catalogue
  }
  deletion_mode = "IMMEDIATE"
  key_value_map = local.pipeline_storage_secrets_kv_map
}

resource "elasticstack_elasticsearch_security_role" "read_only" {
  name = "read_only"

  indices {
    names      = ["images*", "concepts*", "works*"]
    privileges = ["read"]
  }
}

resource "random_password" "read_only_user" { length = 16 }

resource "elasticstack_elasticsearch_security_user" "read_only" {
  username = "read_only"
  password = random_password.read_only_user.result
  roles    = [elasticstack_elasticsearch_security_role.read_only.name]
}

module "readonly_user_secrets" {
  source        = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"
  deletion_mode = "IMMEDIATE"
  key_value_map = {
    "elasticsearch/pipeline_storage_v1/read_only/es_username" = elasticstack_elasticsearch_security_user.read_only.username
    "elasticsearch/pipeline_storage_v1/read_only/es_password" = elasticstack_elasticsearch_security_user.read_only.password
  }
}
