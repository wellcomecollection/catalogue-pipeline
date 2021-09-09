resource "aws_ecs_cluster" "cluster" {
  name               = local.namespace_hyphen
  capacity_providers = [module.inference_capacity_provider_tei_off.name, module.inference_capacity_provider_tei_on.name]
}

module "inference_capacity_provider_tei_on" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/ec2_capacity_provider?ref=v3.5.1"

  name = "${local.namespace_hyphen}_inferrer-tei-on"

  // Setting this variable from aws_ecs_cluster.cluster.name creates a cycle
  // The cluster name is required for the instance user data script
  // This is a known issue https://github.com/terraform-providers/terraform-provider-aws/issues/12739
  cluster_name = local.namespace_hyphen

  instance_type           = "c5.2xlarge"
  max_instances           = 6
  use_spot_purchasing     = true
  scaling_action_cooldown = 240

  subnets = var.subnets
  security_group_ids = [
    aws_security_group.service_egress.id,
  ]
}

module "inference_capacity_provider_tei_off" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/ec2_capacity_provider?ref=v3.5.1"

  name = "${local.namespace_hyphen}_inferrer-tei-off"

  // Setting this variable from aws_ecs_cluster.cluster.name creates a cycle
  // The cluster name is required for the instance user data script
  // This is a known issue https://github.com/terraform-providers/terraform-provider-aws/issues/12739
  cluster_name = local.namespace_hyphen

  instance_type           = "c5.2xlarge"
  max_instances           = 6
  use_spot_purchasing     = true
  scaling_action_cooldown = 240

  subnets = var.subnets
  security_group_ids = [
    aws_security_group.service_egress.id,
  ]
}
