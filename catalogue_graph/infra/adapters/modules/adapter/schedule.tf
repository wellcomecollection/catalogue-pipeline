resource "aws_scheduler_schedule" "adapter_run" {
  name = "${var.namespace}_adapter_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = var.schedule_expression
  state               = "ENABLED"

  target {
    arn      = aws_sfn_state_machine.state_machine.arn
    role_arn = aws_iam_role.eventbridge_state_machine_role.arn
  }
}

# Attach the policy to the EventBridge role
resource "aws_iam_role_policy_attachment" "eventbridge_state_machine_policy_attachment" {
  role       = aws_iam_role.eventbridge_state_machine_role.name
  policy_arn = aws_iam_policy.eventbridge_state_machine_policy.arn
}

# IAM Role for EventBridge to trigger State Machine
resource "aws_iam_role" "eventbridge_state_machine_role" {
  name = "${var.namespace}-adapter-eventbridge-state-machine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = [
            "scheduler.amazonaws.com"
          ]
        },
        Action = "sts:AssumeRole"
      }
    ]
  })
}
