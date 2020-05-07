locals {
  namespace_hyphen = replace(var.namespace, "_", "-")

  id_minter_service_count        = 2 // images and works
  id_minter_task_max_connections = 9
  // The max number of connections allowed by the instance
  // specified at /infrastructure/critical/rds_id_minter.tf
  id_minter_rds_max_connections = 90
}
