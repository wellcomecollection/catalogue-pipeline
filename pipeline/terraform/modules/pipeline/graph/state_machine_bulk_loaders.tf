module "catalogue_graph_bulk_loaders_monthly_state_machine" {
  source = "../../state_machine"
  name   = "graph-bulk-loaders-monthly-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    Comment = "Trigger the catalogue-graph-bulk-loader state machine in sequence for each combination of inputs."
    StartAt = "Load ${local.concepts_pipeline_inputs_monthly[0].label}"
    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_monthly :
      "Load ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = module.catalogue_graph_bulk_loader_state_machine.state_machine_arn
          Input = {
            "transformer_type" : task_input.transformer_type,
            "entity_type" : task_input.entity_type,
            "pipeline_date" : var.pipeline_date,
            "insert_error_threshold" : try(task_input.insert_error_threshold, local.bulk_loader_default_insert_error_threshold),
          }
        }
        Next = index == length(local.concepts_pipeline_inputs_monthly) - 1 ? "Success" : "Load ${local.concepts_pipeline_inputs_monthly[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_bulk_loader_state_machine.state_machine_arn
  ]
}

module "catalogue_graph_bulk_loaders_incremental_state_machine" {
  source = "../../state_machine"
  name   = "graph-bulk-loaders-incremental-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Trigger the catalogue-graph-bulk-loader state machine in sequence for each combination of inputs."
    StartAt       = "Load ${local.concepts_pipeline_inputs_incremental[0].label}"
    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_incremental :
      "Load ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = module.catalogue_graph_bulk_loader_state_machine.state_machine_arn
          Input = {
            "transformer_type" : task_input.transformer_type,
            "entity_type" : task_input.entity_type,
            "pipeline_date" : "{% $states.context.Execution.Input.pipeline_date %}",
            "window" : "{% $states.context.Execution.Input.window ? $states.context.Execution.Input.window : null %}",
            "insert_error_threshold" : try(task_input.insert_error_threshold, local.bulk_loader_default_insert_error_threshold),
          }
        }
        Next = index == length(local.concepts_pipeline_inputs_incremental) - 1 ? "Success" : "Load ${local.concepts_pipeline_inputs_incremental[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_bulk_loader_state_machine.state_machine_arn
  ]
}
