module "input_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs.git//queue?ref=v1.4.0"

  queue_name = "${var.name}-pipe-queue"

  topic_arns                 = [var.sns_topic_arn]
  visibility_timeout_seconds = var.queue_visibility_timeout_seconds
  max_receive_count          = var.queue_max_receive_count
  message_retention_seconds  = var.queue_message_retention_seconds
  alarm_topic_arn            = var.dlq_alarm_arn
}

resource "aws_pipes_pipe" "pipe" {
  name     = var.name
  role_arn = aws_iam_role.pipe_role.arn

  source = module.input_queue.arn
  target = var.state_machine_arn

  source_parameters {
    sqs_queue_parameters {
      batch_size                         = var.batch_size
      maximum_batching_window_in_seconds = var.maximum_batching_window_in_seconds
    }
  }

  target_parameters {
    step_function_state_machine_parameters {
      invocation_type = "FIRE_AND_FORGET"
    }
  }

  desired_state = var.enabled ? "RUNNING" : "STOPPED"
}
