module "catalogue_graph_ingestors_state_machine" {
  source = "../../state_machine"
  name   = "graph-pipeline-ingestors-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue works and concepts into the pipeline Elasticsearch cluster."
    StartAt       = "Ingestors"

    States = {
      "Ingestors" = {
        Type           = "Map",
        Items          = local.ingestor_types
        MaxConcurrency = 2

        ItemSelector = {
          "ingestor_type" : "{% $states.context.Map.Item.Value %}",
          "pipeline_date" : "{% $states.context.Execution.Input.pipeline_date %}",
          "index_date" : "{% $lookup($states.context.Execution.Input.index_dates, $states.context.Map.Item.Value) %}",
          "window" : "{% $states.context.Execution.Input.window ? $states.context.Execution.Input.window : null %}",
          "pit_id" : "{% $states.context.Execution.Input.pit_id ? $states.context.Execution.Input.pit_id : null %}",
        }

        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Run ingestor",
          States = {
            "Run ingestor" = {
              Type     = "Task",
              Resource = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = module.catalogue_graph_ingestor_state_machine.state_machine_arn
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
    module.catalogue_graph_ingestor_state_machine.state_machine_arn
  ]
}
