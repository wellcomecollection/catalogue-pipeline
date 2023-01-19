locals {
  extra_rds_instances = var.reindexing_state.scale_up_id_minter_db ? 2 : 0
}

resource "aws_rds_cluster_instance" "extra_instances" {
  count = local.extra_rds_instances

  identifier           = "pipeline-${var.pipeline_date}-extra-capacity-${count.index}"
  cluster_identifier   = var.rds_config.cluster_id
  instance_class       = "db.t3.medium"
  db_subnet_group_name = var.rds_config.subnet_group
  publicly_accessible  = false
  engine = "aurora-mysql"
}
