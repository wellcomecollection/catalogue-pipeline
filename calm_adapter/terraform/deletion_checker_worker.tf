module "calm_deletion_checker_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.1.2"
  queue_name                 = "calm-deletion-checker-input"
  topic_arns                 = [local.calm_deletion_checker_topic_arn]
  aws_region                 = local.aws_region
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = 30 * 60
}

module "deletion_checker_worker" {
  source = "../../infrastructure/modules/worker"

  name = "calm-deletion-checker"

  image = local.calm_deletion_checker_image

  env_vars = {
    calm_api_url          = local.calm_api_url
    queue_url             = module.calm_deletion_checker_queue.url
    topic_arn             = module.calm_deletions_topic.arn
    vhs_dynamo_table_name = module.vhs.table_name
    batch_size            = 1000
  }
  secret_env_vars = {
    calm_api_username = "calm_adapter/calm_api/username"
    calm_api_password = "calm_adapter/calm_api/password"
  }

  min_capacity = 0
  max_capacity = 2

  cpu    = 512
  memory = 1024

  cluster_name           = aws_ecs_cluster.cluster.name
  cluster_arn            = aws_ecs_cluster.cluster.arn
  subnets                = local.private_subnets
  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
}

resource "aws_iam_role_policy" "read_from_deletion_checker_queue" {
  role   = module.deletion_checker_worker.task_role_name
  policy = module.calm_deletion_checker_queue.read_policy
}


module "deletion_checker_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.1.2"
  queue_name = module.calm_deletion_checker_queue.name

  queue_high_actions = [
    module.deletion_checker_worker.scale_up_arn
  ]

  queue_low_actions = [
    module.deletion_checker_worker.scale_down_arn
  ]
}
