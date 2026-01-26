resource "aws_ecs_cluster" "cluster" {
  name = local.namespace

  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}

resource "aws_ecs_cluster_capacity_providers" "cluster" {
  cluster_name       = aws_ecs_cluster.cluster.name
  capacity_providers = [module.inference_capacity_provider.name]
}

module "inference_capacity_provider" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/ec2_capacity_provider?ref=v4.3.0"

  name = "${local.namespace}_inferrer"

  cluster_name = aws_ecs_cluster.cluster.name

  # When we're not reindexing, we halve the size of these instances and
  # the corresponding tasks, because they won't be getting as many updates.
  #
  # Note: although we only run one task at a time when we're not reindexing,
  # we need to allow spiking to 2 instances, because when ECS does a
  # blue-green deployment of an image inferrer task, it's (briefly) running
  # two tasks at once: the old task and the new task.
  instance_type = var.reindexing_state.scale_up_tasks ? "c5.2xlarge" : "c5.xlarge"
  max_instances = var.reindexing_state.scale_up_tasks ? 12 : 2

  use_spot_purchasing     = true
  scaling_action_cooldown = 240

  subnets = local.network_config.subnets
  security_group_ids = [
    aws_security_group.egress.id,
  ]

  ami_id = var.ami_id
}
