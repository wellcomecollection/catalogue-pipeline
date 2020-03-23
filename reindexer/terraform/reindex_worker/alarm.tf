module "sqs_autoscaling_alarms" {
  source = "github.com/wellcomecollection/terraform-aws-sqs.git//autoscaling?ref=v1.1.2"

  queue_name = module.reindex_worker_queue.name

  queue_low_actions = [
    module.service.scale_down_arn
  ]

  queue_high_actions = [
    module.service.scale_up_arn
  ]
}
