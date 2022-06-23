resource "aws_ecs_cluster" "cluster" {
  name               = local.namespace
  capacity_providers = [module.inference_capacity_provider.name]
}

module "inference_capacity_provider" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/ec2_capacity_provider?ref=v3.12.1"

  name = "${local.namespace}_inferrer"

  // Setting this variable from aws_ecs_cluster.cluster.name creates a cycle
  // The cluster name is required for the instance user data script
  // This is a known issue https://github.com/terraform-providers/terraform-provider-aws/issues/12739
  cluster_name = local.namespace

  # When we're not reindexing, we halve the size of these instances and
  # the corresponding tasks, because they won't be getting as many updates.
  instance_type           = var.reindexing_state.scale_up_tasks ? "c5.2xlarge" : "c5.xlarge"
  max_instances           = var.reindexing_state.scale_up_tasks ? 12 : 1
  use_spot_purchasing     = true
  scaling_action_cooldown = 240

  subnets = var.subnets
  security_group_ids = [
    aws_security_group.egress.id,
  ]
}
