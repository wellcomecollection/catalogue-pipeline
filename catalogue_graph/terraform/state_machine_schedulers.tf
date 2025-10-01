resource "aws_scheduler_schedule" "concepts_pipeline_monthly" {
  name = "concepts_pipeline_monthly_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "cron(20 9 ? 1/1 MON#1 *)" # 1st Monday of the month at 9:20am

  target {
    arn      = aws_sfn_state_machine.concepts_pipeline_monthly.arn
    role_arn = aws_iam_role.state_machine_execution_role.arn
  }
}

resource "aws_scheduler_schedule" "concepts_pipeline_incremental" {
  name                = "concepts_pipeline_incremental_run"
  schedule_expression = "rate(15 minutes)"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:sfn:startExecution"
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = jsonencode({
      # StateMachineArn = aws_sfn_state_machine.concepts_pipeline_daily.arn,
      StateMachineArn = aws_sfn_state_machine.catalogue_graph_ingestor.arn,
      Input = jsonencode({
        ingestor_type = "concepts"
        pipeline_date = local.pipeline_date
        # TODO: Replace with production index
        index_date = "dev"
        window = {
          end_time = "<aws.scheduler.scheduled-time>"
        }
      })
    })
  }
}
