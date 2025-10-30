resource "aws_scheduler_schedule" "concepts_pipeline_monthly" {
  name = "concepts_pipeline_monthly_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "cron(20 9 ? 1/1 MON#1 *)" # 1st Monday of the month at 9:20am

  target {
    arn      = module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn
    role_arn = aws_iam_role.state_machine_execution_role.arn
  }
}

resource "aws_scheduler_schedule" "catalogue_graph_pipeline_incremental" {
  name                = "catalogue_graph_pipeline_incremental_run"
  schedule_expression = "cron(0,15,30,45 * * * ? *)" # Every 15 minutes

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = <<JSON
    {
      "pipeline_date": "${var.pipeline_date}",
      "index_dates": {
        "concepts": "${var.index_dates.concepts}",
        "works": "${var.index_dates.works}"
      },
      "window": {
        "end_time": "<aws.scheduler.scheduled-time>"
      }
    }
    JSON
  }
}

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
