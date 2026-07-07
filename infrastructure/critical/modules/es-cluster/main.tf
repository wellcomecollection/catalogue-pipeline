locals {
  # deployment_name = replace(var.cluster_date, "_", "-")

  elastic_id     = ec_deployment.cluster.elasticsearch.resource_id
  elastic_region = ec_deployment.cluster.elasticsearch.region

  secrets_kv_map = {
    "elasticsearch/es-cluster-${var.cluster_date}/public_host"  = "${local.elastic_id}.${local.elastic_region}.aws.found.io"
    "elasticsearch/es-cluster-${var.cluster_date}/private_host" = "${local.elastic_id}.vpce.${local.elastic_region}.aws.elastic-cloud.com"
    "elasticsearch/es-cluster-${var.cluster_date}/protocol"     = split(":", ec_deployment.cluster.elasticsearch.https_endpoint)[0]
    "elasticsearch/es-cluster-${var.cluster_date}/port"         = reverse(split(":", ec_deployment.cluster.elasticsearch.https_endpoint))[0]
    "elasticsearch/es-cluster-${var.cluster_date}/es_username"  = ec_deployment.cluster.elasticsearch_username
    "elasticsearch/es-cluster-${var.cluster_date}/es_password"  = ec_deployment.cluster.elasticsearch_password
  }
}

data "ec_stack" "latest_patch" {
  version_regex = var.version_regex
  region        = "eu-west-1"
}

resource "ec_deployment" "cluster" {
  name                   = "es-cluster-${var.cluster_date}"
  version                = data.ec_stack.latest_patch.version
  region                 = "eu-west-1"
  deployment_template_id = var.deployment_template
  traffic_filter = var.traffic_filter_ids

  elasticsearch = {
    hot = {
      size        = var.memory
      zone_count  = var.node_count
      autoscaling = {}
    }
  }

  kibana = {
    size       = "1g"
    zone_count = 1
  }

  observability = {
    deployment_id = var.logging_cluster_id
  }

  lifecycle { ignore_changes = [version] }
}

module "secrets" {
  source        = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"
  deletion_mode = "IMMEDIATE"
  key_value_map = local.secrets_kv_map
}

module "secrets_catalogue" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.4.0"
  providers = {
    aws = aws.catalogue
  }
  deletion_mode = "IMMEDIATE"
  key_value_map = local.secrets_kv_map
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
    "elasticsearch/${var.cluster_date}/read_only/es_username" = elasticstack_elasticsearch_security_user.read_only.username
    "elasticsearch/${var.cluster_date}/read_only/es_password" = elasticstack_elasticsearch_security_user.read_only.password
  }
}
