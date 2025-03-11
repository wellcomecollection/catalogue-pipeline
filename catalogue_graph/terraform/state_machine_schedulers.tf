resource "aws_scheduler_schedule" "concept_pipeline_monthly" {
  name = "concept_pipeline_monthly_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "20 9 ? 1/1 MON#1 *" # 1st Monday of the month at 9:20am

  target {
    arn      = aws_sfn_state_machine.concepts_pipeline.arn
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = jsonencode({
      MessageBody = var.state_machine_monthly_inputs
    })
  }
}

resource "aws_scheduler_schedule" "concept_pipeline_daily" {
  name = "concept_pipeline_daily_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "20 14 ? * MON-THU *" # MON-THU 2:20pm

  target {
    arn      = aws_sfn_state_machine.concepts_pipeline.arn
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = jsonencode({
      MessageBody = var.state_machine_daily_inputs
    })
  }
}