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

# Sierra Hybrid Store

output "vhs_sierra_full_access_policy" {
  value = module.vhs_sierra.full_access_policy
}

output "vhs_sierra_read_policy" {
  value = module.vhs_sierra.read_policy
}

output "vhs_sierra_table_name" {
  value = module.vhs_sierra.table_name
}

output "vhs_sierra_bucket_name" {
  value = module.vhs_sierra.bucket_name
}

output "vhs_sierra_assumable_read_role" {
  value = module.vhs_sierra.assumable_read_role
}

# Mets Store

output "mets_dynamo_full_access_policy" {
  value = data.aws_iam_policy_document.mets_dynamo_full_access_policy.json
}

output "mets_dynamo_read_policy" {
  value = data.aws_iam_policy_document.mets_dynamo_read_policy.json
}

output "mets_dynamo_table_name" {
  value = aws_dynamodb_table.mets_adapter_table.id
}

# Sierra Items Hybrid Store

output "vhs_sierra_items_full_access_policy" {
  value = module.vhs_sierra_items.full_access_policy
}

output "vhs_sierra_items_table_name" {
  value = module.vhs_sierra_items.table_name
}

output "vhs_sierra_items_bucket_name" {
  value = module.vhs_sierra_items.bucket_name
}

output "vhs_sierra_items_assumable_read_role" {
  value = module.vhs_sierra_items.assumable_read_role
}

# Calm Hybrid Store
output "vhs_calm_read_policy" {
  value = module.vhs_calm.read_policy
}

output "vhs_calm_table_name" {
  value = module.vhs_calm.table_name
}

output "vhs_calm_bucket_name" {
  value = module.vhs_calm.bucket_name
}

output "vhs_calm_assumable_read_role" {
  value = module.vhs_calm.assumable_read_role
}

