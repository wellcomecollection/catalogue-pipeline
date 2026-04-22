# RDS

locals {
  id_minter_rds_instances = {
    prod = module.id_minter_rds
    test = module.id_minter_rds_test
  }
}

output "id_minter_rds" {
  value = {
    for name, instance in local.id_minter_rds_instances : name => {
      cluster_id                = instance.rds_cluster_id
      cluster_arn               = instance.rds_cluster_arn
      ingress_security_group_id = instance.ingress_security_group_id
      master_user_secret_arn    = instance.master_user_secret_arn
      subnet_group_name         = instance.subnet_group_name
    }
  }
}

# Legacy flat outputs for the prod cluster — retained for backwards
# compatibility with downstream stacks reading via terraform_remote_state.
output "rds_v2_serverless_cluster_id" {
  value = module.id_minter_rds.rds_cluster_id
}

output "rds_v2_serverless_cluster_arn" {
  value = module.id_minter_rds.rds_cluster_arn
}

output "rds_v2_access_security_group_id" {
  value = module.id_minter_rds.ingress_security_group_id
}

output "rds_v2_master_user_secret_arn" {
  value = module.id_minter_rds.master_user_secret_arn
}

output "rds_subnet_group_name" {
  value = module.id_minter_rds.subnet_group_name
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
