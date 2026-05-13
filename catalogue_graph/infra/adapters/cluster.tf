locals {
  vpc_id          = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_private_subnets
}

resource "aws_ecs_cluster" "adapters" {
  name = "catalogue-adapters"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_security_group" "adapter_egress" {
  name        = "catalogue-adapters-egress"
  description = "Allow all egress for adapter ECS tasks"
  vpc_id      = local.vpc_id
}

resource "aws_vpc_security_group_egress_rule" "allow_all" {
  security_group_id = aws_security_group.adapter_egress.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

data "aws_ecr_repository" "unified_pipeline_task" {
  name = "uk.ac.wellcome/unified_pipeline_task"
}
