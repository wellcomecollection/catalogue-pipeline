resource "aws_scheduler_schedule" "schedule" {
  name                = "${local.slug}-schedule-${var.pipeline_date}"
  schedule_expression = var.schedule.cron

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.state_machine.state_machine_arn
    role_arn = aws_iam_role.run_state_machine.arn

    input = <<JSON
    {
      "scheduled_time": "<aws.scheduler.scheduled-time>"
    }
    JSON
  }

  state = var.schedule.enabled ? "ENABLED" : "DISABLED"
}

resource "aws_iam_role" "run_state_machine" {
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
  role = aws_iam_role.run_state_machine.id

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
