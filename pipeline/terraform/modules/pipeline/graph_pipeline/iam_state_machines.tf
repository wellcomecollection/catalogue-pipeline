data "aws_caller_identity" "current" {}

resource "aws_iam_role" "state_machine_execution_role" {
  name = "${local.namespace}-state-machine-execution-role-${var.pipeline_date}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = [
            "states.amazonaws.com",
            "scheduler.amazonaws.com"
          ]
        },
        Action = "sts:AssumeRole"
      }
    ]
  })
}
