resource "aws_scheduler_schedule" "graph_pipeline_monthly" {
  name = "graph-pipeline-monthly-run-${var.pipeline_date}"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "cron(20 9 ? 1/1 MON#1 *)" # 1st Monday of the month at 9:20am

  target {
    arn      = module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_graph_pipeline_role.arn
  }
}

resource "aws_scheduler_schedule" "catalogue_graph_pipeline_incremental" {
  name                = "graph-pipeline-incremental-run-${var.pipeline_date}"
  schedule_expression = "cron(0,15,30,45 * * * ? *)" # Every 15 minutes

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_graph_pipeline_role.arn

    input = <<JSON
    {
      "pipeline_date": "${var.pipeline_date}",
      "index_dates": {
        "merged": "${var.index_dates.merged}",
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

resource "aws_iam_role" "run_graph_pipeline_role" {
  name = "run-graph-pipeline-role-${var.pipeline_date}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "scheduler.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_policy" "start_graph_pipeline" {
  name = "start-graph-pipeline-${var.pipeline_date}"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "states:StartExecution"
        Resource = [
          module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn,
          module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "attach_policy" {
  role       = aws_iam_role.run_graph_pipeline_role.name
  policy_arn = aws_iam_policy.start_graph_pipeline.arn
}
