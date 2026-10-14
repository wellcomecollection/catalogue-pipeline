resource "ec_elasticsearch_project" "project" {
  name          = var.name
  region_id     = var.region_id
  optimized_for = "general_purpose"

  traffic_filter_ids = var.traffic_filter_ids

  metadata = {
    tags = var.tags
  }

  # search_lake is left unset so the defaults can be read back, and the
  # On-demand tier set in the console must not be reverted by a plan.
  lifecycle {
    ignore_changes = [search_lake]
  }
}

resource "elasticstack_elasticsearch_security_api_key" "bootstrap" {
  name = "${var.name}-terraform-bootstrap"

  role_descriptors = jsonencode({
    bootstrap = {
      # monitor: the elasticstack provider reads the root endpoint before every resource.
      cluster = ["manage_api_key", "manage_pipeline", "monitor"]
      indices = [
        {
          names                    = ["*"]
          privileges               = ["all"]
          allow_restricted_indices = false
        }
      ]
    }
  })
}
