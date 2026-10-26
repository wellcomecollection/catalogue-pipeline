# Only for scheduled planners; event-triggered ones target the state machine
# from their own EventBridge rules.
resource "aws_scheduler_schedule" "schedule" {
  count = var.schedule == null ? 0 : 1

  name                = "${local.slug}-schedule-${var.pipeline_date}"
  schedule_expression = var.schedule.cron

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.state_machine.state_machine_arn
    role_arn = aws_iam_role.run_state_machine[0].arn

    input = <<JSON
    {
      "scheduled_time": "<aws.scheduler.scheduled-time>"
    }
    JSON
  }

  state = var.schedule.enabled ? "ENABLED" : "DISABLED"
}

resource "aws_iam_role" "run_state_machine" {
  count = var.schedule == null ? 0 : 1

  name = "run-${local.slug}-role-${var.pipeline_date}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "scheduler.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "run_state_machine" {
  count = var.schedule == null ? 0 : 1

  role = aws_iam_role.run_state_machine[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "states:StartExecution"
        Resource = module.state_machine.state_machine_arn
      }
    ]
  })
}

moved {
  from = aws_scheduler_schedule.schedule
  to   = aws_scheduler_schedule.schedule[0]
}

moved {
  from = aws_iam_role.run_state_machine
  to   = aws_iam_role.run_state_machine[0]
}

moved {
  from = aws_iam_role_policy.run_state_machine
  to   = aws_iam_role_policy.run_state_machine[0]
}
