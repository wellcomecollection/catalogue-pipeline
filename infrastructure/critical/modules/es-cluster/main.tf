locals {
  elastic_id     = ec_deployment.cluster.elasticsearch.resource_id
  elastic_region = ec_deployment.cluster.elasticsearch.region
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
