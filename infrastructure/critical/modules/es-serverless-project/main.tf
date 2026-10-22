resource "ec_elasticsearch_project" "project" {
  name          = var.name
  region_id     = var.region_id
  optimized_for = "general_purpose"

  traffic_filter_ids = var.traffic_filter_ids

  metadata = {
    tags = var.tags
  }

  # Unset leaves the project defaults (search power 100, boost window 7).
  search_lake = var.search_power == null && var.boost_window == null ? null : {
    search_power = var.search_power
    boost_window = var.boost_window
  }
}
