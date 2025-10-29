resource "aws_sfn_state_machine" "catalogue_graph_ingestor" {
  name     = "${local.namespace}-ingestor-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue works/concepts into the pipeline Elasticsearch cluster."
    StartAt       = "Run loader"
    States = {
      "Run loader" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Monitor loader"
      }
      "Monitor loader" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Run indexer"
      },
      "Run indexer" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_indexer_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next = "Monitor indexer"
      }
      "Monitor indexer" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_indexer_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Next = "Should run deletions?"
      },
      "Should run deletions?" = {
        Type = "Choice"
        Choices = [
          {
            "Condition" : "{% $states.input.ingestor_type = 'concepts' %}",
            "Next" : "Run deletions"
          }
        ]
        Default = "Success"
      },
      "Run deletions" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_deletions_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}


resource "aws_sfn_state_machine" "catalogue_graph_ingestors" {
  name     = "${local.namespace}-ingestors-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
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
                StateMachineArn = aws_sfn_state_machine.catalogue_graph_ingestor.arn
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
}

