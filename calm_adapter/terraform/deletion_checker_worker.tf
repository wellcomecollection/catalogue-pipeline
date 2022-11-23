module "calm_deletion_checker_queue" {
  source                     = "git::github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"
  queue_name                 = "calm-deletion-checker-input"
  topic_arns                 = [local.calm_deletion_checker_topic_arn]
  alarm_topic_arn            = local.dlq_alarm_arn
  visibility_timeout_seconds = 30 * 60
}

module "deletion_checker_worker" {
  source = "../../infrastructure/modules/worker"

  name = "calm_deletion_checker"

  image = local.calm_deletion_checker_image

  env_vars = {
    calm_api_url          = local.calm_api_url
    queue_url             = module.calm_deletion_checker_queue.url
    topic_arn             = module.calm_deletions_topic.arn
    vhs_dynamo_table_name = module.vhs.table_name
    // Choosing the batch size is a tradeoff between number of requests
    // and the size of those requests; smaller batches mean more requests
    // but with a smaller maximum request size.
    //
    // Given that the Calm API errors before resource exhaustion occurs
    // it seems that batch size might be an issue, so this has been tuned
    // down from 1000.
    batch_size = 512
  }
  secret_env_vars = {
    calm_api_username = "calm_adapter/calm_api/username"
    calm_api_password = "calm_adapter/calm_api/password"
  }

  min_capacity = 0

  // Here be dragons: don't scale this up or else you might
  // knock over the Calm server.
  max_capacity = local.deletion_checking_enabled ? 1 : 0

  cpu    = 512
  memory = 1024

  cluster_name             = aws_ecs_cluster.cluster.name
  cluster_arn              = aws_ecs_cluster.cluster.arn
  subnets                  = local.private_subnets
  shared_logging_secrets   = local.shared_logging_secrets
  elastic_cloud_vpce_sg_id = local.elastic_cloud_vpce_sg_id

  security_group_ids = [
    aws_security_group.egress.id,
  ]

  use_fargate_spot = true
}

resource "aws_iam_role_policy" "read_from_deletion_checker_queue" {
  role   = module.deletion_checker_worker.task_role_name
  policy = module.calm_deletion_checker_queue.read_policy
}


module "deletion_checker_scaling_alarm" {
  source     = "git::github.com/wellcomecollection/terraform-aws-sqs//autoscaling?ref=v1.2.1"
  queue_name = module.calm_deletion_checker_queue.name

  queue_high_actions = [
    module.deletion_checker_worker.scale_up_arn
  ]

  queue_low_actions = [
    module.deletion_checker_worker.scale_down_arn
  ]
}
