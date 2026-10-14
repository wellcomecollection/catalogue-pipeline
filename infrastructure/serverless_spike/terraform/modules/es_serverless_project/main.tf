resource "ec_elasticsearch_project" "project" {
  name          = var.name
  region_id     = var.region_id
  optimized_for = "general_purpose"

  traffic_filter_ids = var.traffic_filter_ids

  metadata = {
    tags = var.tags
  }

  # Unset at creation so the defaults could be read back (100 and 7 on 2026-10-14).
  search_lake = var.search_power == null && var.boost_window == null ? null : {
    search_power = var.search_power
    boost_window = var.boost_window
  }
}
