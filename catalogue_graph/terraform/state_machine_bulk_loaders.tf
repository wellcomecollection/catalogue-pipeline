resource "aws_sfn_state_machine" "catalogue_graph_bulk_loaders" {
  name     = "catalogue-graph-bulk-loaders"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Trigger the catalogue-graph-bulk-loader state machine in sequence for each combination of inputs."
    StartAt = "Load ${var.state_machine_inputs[0].label}"
    States  = merge(tomap({
      for index, task_input in var.state_machine_inputs :
      "Load ${task_input.label}" => {
        Type       = "Task"
        Resource   = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input           = {
            "transformer_type" = task_input.transformer_type,
            "entity_type"      = task_input.entity_type
          }
        }
        Next = index == length(var.state_machine_inputs) - 1 ? "Success" : "Load ${var.state_machine_inputs[index + 1].label}"
      }
    }), {
      Success = {
        Type = "Succeed"
      }
    })

  })
}
resource "aws_sfn_state_machine" "catalogue_graph_bulk_loaders_monthly" {
  name     = "catalogue-graph-bulk-loaders_monthly"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Trigger the catalogue-graph-bulk-loader state machine in sequence for each combination of inputs."
    StartAt = "Load ${local.concepts_pipeline_inputs_monthly[0].label}"
    States  = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_monthly :
      "Load ${task_input.label}" => {
        Type       = "Task"
        Resource   = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input           = task_input
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
    States  = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_daily :
      "Load ${task_input.label}" => {
        Type       = "Task"
        Resource   = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input           = task_input
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
