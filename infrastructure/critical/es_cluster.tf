module "es_cluster_2026-07-03" {
  source = "./modules/es-cluster"
  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }

  cluster_name = "es-cluster-2026-07-03"

  traffic_filter_ids = [
    data.terraform_remote_state.shared_infra.outputs["ec_platform_privatelink_traffic_filter_id"],
    data.terraform_remote_state.shared_infra.outputs["ec_catalogue_privatelink_traffic_filter_id"],
    data.terraform_remote_state.shared_infra.outputs["ec_public_internet_traffic_filter_id"],
  ]
  logging_cluster_id = data.terraform_remote_state.shared_infra.outputs["logging_cluster_id"]
}
