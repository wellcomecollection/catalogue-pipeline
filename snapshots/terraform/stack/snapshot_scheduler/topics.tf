module "scheduler_topic" {
  source = "git::github.com/wellcomecollection/terraform-aws-sns-topic?ref=v1.0.1"

  name = "snapshot_schedule-${var.deployment_service_env}"
}

resource "aws_iam_role_policy" "allow_scheduler_to_publish_to_topic" {
  role   = module.snapshot_scheduler.role_name
  policy = module.scheduler_topic.publish_policy
}
