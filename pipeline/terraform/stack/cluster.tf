resource "aws_ecs_cluster" "cluster" {
  name               = local.namespace_hyphen
  capacity_providers = [module.inference_capacity_provider.name]
}

module "inference_capacity_provider" {
  source = "../../../../terraform-aws-ecs-service/modules/ec2_capacity_provider"

  name                    = "${local.namespace_hyphen}_inference_capacity_provider"
  instance_type           = "c5.xlarge"
  use_spot_purchasing     = true
  scaling_action_cooldown = 120

  subnets = var.subnets
  security_group_ids = [
    aws_security_group.service_egress.id,
    aws_security_group.interservice.id
  ]
}
