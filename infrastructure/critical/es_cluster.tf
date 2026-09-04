module "es_cluster_2026_07_03" {
  source = "./modules/es-cluster"
  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }

  cluster_date = "2026-07-03"

  # Sized for the round 3 full reindex (platform#6624), matching rounds 1 and 2.
  # The scale-down apply only runs once a day, so it stays here until the
  # comparison signs off rather than being taken down and brought back up.
  memory     = "30g"
  node_count = 2

  traffic_filter_ids = [
    local.shared_infra["ec_platform_privatelink_traffic_filter_id"],
    local.shared_infra["ec_catalogue_privatelink_traffic_filter_id"],
    local.shared_infra["ec_public_internet_traffic_filter_id"],
  ]
  logging_cluster_id = local.shared_infra["logging_cluster_id"]
}
