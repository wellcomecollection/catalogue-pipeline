resource "aws_sfn_state_machine" "catalogue_graph_removers" {
  name     = "catalogue-graph-removers"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Remove unused source concept nodes/edges from the catalogue graph"
    StartAt       = "Run graph removers"
    States        = {
      "Run graph removers" = {
        Type           = "Map",
        Items          = local.concepts_pipeline_inputs_monthly
        MaxConcurrency = 2
        ItemProcessor  = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Remove unused nodes and edges",
          States  = {
            "Remove unused nodes and edges" = {
              Type      = "Task",
              Resource  = "arn:aws:states:::lambda:invoke",
              Output    = "{% $states.result.Payload %}",
              Arguments = {
                FunctionName = module.graph_remover_lambda.lambda.arn,
                Payload      = {
                  "transformer_type" : "{% $states.input.transformer_type %}",
                  "entity_type" : "{% $states.input.entity_type %}",
                  "pipeline_date" : local.pipeline_date,
                }
              },
              Retry = local.DefaultRetry,
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
}

resource "aws_sfn_state_machine" "catalogue_graph_graph_removers_incremental" {
  name     = "catalogue-graph-graph-removers-incremental"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
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
          States  = {
            "Run incremental graph remover" = {
              Type      = "Task",
              Resource  = "arn:aws:states:::lambda:invoke",
              Arguments = {
                FunctionName = module.graph_remover_incremental_lambda.lambda.arn,
                Payload      = "{% $states.input %}"
              },
              Retry = local.DefaultRetry,
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
}
