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
