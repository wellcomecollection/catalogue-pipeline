module "catalogue_graph_removers_full_state_machine" {
  source = "../../state_machine"
  name = "graph-removers-full-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Remove unused nodes/edges from the catalogue graph"
    StartAt       = "Run graph removers"
    States = {
      "Run graph removers" = {
        Type           = "Map",
        Items          = local.concepts_pipeline_inputs_monthly
        MaxConcurrency = 2
        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Remove unused nodes and edges",
          States = {
            "Remove unused nodes and edges" = {
              Type     = "Task",
              Resource = "arn:aws:states:::lambda:invoke",
              Output   = "{% $states.result.Payload %}",
              Arguments = {
                FunctionName = module.graph_remover_lambda.lambda.arn,
                Payload = {
                  "transformer_type" : "{% $states.input.transformer_type %}",
                  "entity_type" : "{% $states.input.entity_type %}",
                  "pipeline_date" : var.pipeline_date,
                }
              },
              Retry = local.state_function_default_retry,
              End   = true
            }
          }
        },
        Next = "Success"
      },
      "Success" : {
        "Type" : "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.graph_remover_lambda.lambda.arn
  ]
}

module "catalogue_graph_removers_incremental_state_machine" {
  source = "../../state_machine"
  name = "graph-removers-incremental-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Remove unused catalogue nodes/edges from the catalogue graph"
    StartAt       = "Graph removers"

    States = {
      "Graph removers" = {
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
          StartAt = "Run incremental graph remover",
          States = {
            "Run incremental graph remover" = {
              Type     = "Task",
              Resource = "arn:aws:states:::lambda:invoke",
              Arguments = {
                FunctionName = module.graph_remover_incremental_lambda.lambda.arn,
                Payload      = "{% $states.input %}"
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

  invokable_lambda_arns = [
    module.graph_remover_incremental_lambda.lambda.arn
  ]
}
