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
