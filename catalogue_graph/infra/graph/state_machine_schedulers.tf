resource "aws_scheduler_schedule" "concepts_pipeline_monthly" {
  name = "concepts_pipeline_monthly_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "cron(20 9 ? 1/1 MON#1 *)" # 1st Monday of the month at 9:20am

  target {
    arn      = aws_sfn_state_machine.catalogue_graph_pipeline_monthly.arn
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
    arn      = aws_sfn_state_machine.catalogue_graph_pipeline_incremental.arn
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = <<JSON
    {
      "pipeline_date": "${local.pipeline_date}",
      "index_dates": {
        "concepts": "${local.index_date_concepts}",
        "works": "${local.index_date_works}"
      },
      "window": {
        "end_time": "<aws.scheduler.scheduled-time>"
      }
    }
    JSON
  }
}
