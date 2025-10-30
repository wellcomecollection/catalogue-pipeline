module "catalogue_graph_extractors_monthly_state_machine" {
  source = "../../state_machine"
  name   = "graph-extractors-monthly-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    Comment = "Transform raw concepts from external sources into nodes and edges and stream them into an S3 bucket."
    StartAt = "Extract ${local.concepts_pipeline_inputs_monthly[0].label}"

    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_monthly :
      "Extract ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = module.catalogue_graph_extractor_state_machine.state_machine_arn
          Input = {
            "transformer_type" : task_input.transformer_type,
            "entity_type" : task_input.entity_type,
            "pipeline_date" : var.pipeline_date,
            "sample_size" : contains(keys(task_input), "sample_size") ? task_input.sample_size : null,
          }
        }
        Next = index == length(local.concepts_pipeline_inputs_monthly) - 1 ? "Success" : "Extract ${local.concepts_pipeline_inputs_monthly[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_extractor_state_machine.state_machine_arn
  ]
}

module "catalogue_graph_extractors_incremental_state_machine" {
  source = "../../state_machine"
  name   = "graph-extractors-incremental-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Extract catalogue works/concepts, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt       = "Extractors"

    States = {
      "Extractors" = {
        Type           = "Map",
        Items          = local.concepts_pipeline_inputs_incremental
        MaxConcurrency = 10

        ItemSelector = {
          "transformer_type" : "{% $states.context.Map.Item.Value.transformer_type %}",
          "entity_type" : "{% $states.context.Map.Item.Value.entity_type %}",
          "pipeline_date" : "{% $states.context.Execution.Input.pipeline_date %}",
          "window" : "{% $states.context.Execution.Input.window ? $states.context.Execution.Input.window : null %}",
          "pit_id" : "{% $states.context.Execution.Input.pit_id ? $states.context.Execution.Input.pit_id : null %}",
        }

        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Run extractor",
          States = {
            "Run extractor" = {
              Type     = "Task",
              Resource = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = module.catalogue_graph_extractor_state_machine.state_machine_arn
                Input           = "{% $states.input %}"
              },
              Retry = local.state_function_default_retry,
              End   = true
            }
          }
        },
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_extractor_state_machine.state_machine_arn
  ]
}
