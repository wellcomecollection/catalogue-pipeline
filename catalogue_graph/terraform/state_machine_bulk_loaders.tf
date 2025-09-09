resource "aws_sfn_state_machine" "catalogue_graph_bulk_loaders_monthly" {
  name     = "catalogue-graph-bulk-loaders_monthly"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Trigger the catalogue-graph-bulk-loader state machine in sequence for each combination of inputs."
    StartAt = "Load ${local.concepts_pipeline_inputs_monthly[0].label}"
    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_monthly :
      "Load ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input           = jsonencode(task_input)
        }
        Next = index == length(local.concepts_pipeline_inputs_monthly) - 1 ? "Success" : "Load ${local.concepts_pipeline_inputs_monthly[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })
}

resource "aws_sfn_state_machine" "catalogue_graph_bulk_loaders_daily" {
  name     = "catalogue-graph-bulk-loaders_daily"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Trigger the catalogue-graph-bulk-loader state machine in sequence for each combination of inputs."
    StartAt = "Load ${local.concepts_pipeline_inputs_daily[0].label}"
    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_daily :
      "Load ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input = {
            "transformer_type" : task_input.transformer_type,
            "entity_type" : task_input.entity_type,
            "pipeline_date" : local.pipeline_date,
            "insert_error_threshold" : try(task_input.insert_error_threshold, local.bulk_loader_default_insert_error_threshold),
          }
        }
        Next = index == length(local.concepts_pipeline_inputs_daily) - 1 ? "Success" : "Load ${local.concepts_pipeline_inputs_daily[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })
}
