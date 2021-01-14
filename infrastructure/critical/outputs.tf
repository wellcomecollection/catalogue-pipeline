# RDS

output "rds_access_security_group_id" {
  value = aws_security_group.rds_ingress_security_group.id
}

# Miro Hybrid Store

output "vhs_miro_read_policy" {
  value = module.vhs_miro.read_policy
}

output "vhs_miro_table_name" {
  value = module.vhs_miro.table_name
}

output "vhs_miro_assumable_read_role" {
  value = module.vhs_miro.assumable_read_role
}

# Miro Inventory Hybrid Store
output "vhs_miro_inventory_read_policy" {
  value = module.vhs_miro_migration.read_policy
}

output "vhs_miro_inventory_table_name" {
  value = module.vhs_miro_migration.table_name
}

output "vhs_miro_inventory_assumable_read_role" {
  value = module.vhs_miro_migration.assumable_read_role
}

# Catalogue Pipeline Elastic Cloud

output "catalogue_pipeline_storage_elastic_cloud_sg_id" {
  value = aws_security_group.allow_catalogue_pipeline_elastic_cloud_vpce.id
}

output "catalogue_pipeline_ec_privatelink_host" {
  value = local.catalogue_pipeline_ec_privatelink_host
}
