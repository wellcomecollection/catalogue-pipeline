locals {
  DefaultErrorEquals = [
    "Lambda.ServiceException",
    "Lambda.AWSLambdaException",
    "Lambda.SdkClientException",
    "Lambda.TooManyRequestsException"
  ]
  DefaultRetry = [
    {
      ErrorEquals     = local.DefaultErrorEquals
      IntervalSeconds = 1
      MaxAttempts     = 3
      BackoffRate     = 2
      JitterStrategy  = "FULL"
    }
  ]
}

resource "aws_sfn_state_machine" "catalogue_graph_ingestor" {
  name     = "catalogue-graph-ingestor"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue concept data into the pipeline cluster."
    StartAt       = "Trigger ingest"
    States = {
      "Trigger ingest" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_trigger_lambda.lambda.arn,
          Payload = {
            pipeline_date = local.pipeline_date
            index_date    = local.concepts_index_date
          }
        },
        Next = "Monitor trigger ingest"
      },
      "Monitor trigger ingest" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_trigger_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.DefaultRetry,
        Next  = "Scale up cluster"
      },
      "Scale up cluster" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Output   = "{% $states.input %}",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_scaler.arn
          Input = {
            min_capacity = 24,
            max_capacity = 32
          }
        },
        Retry = local.DefaultRetry,
        Next  = "Map load to s3"
      }
      # the next step is a state map that takes the json list output of the ingestor_trigger_lambda and maps it to a list of ingestor tasks
      "Map load to s3" = {
        Type           = "Map",
        MaxConcurrency = 3
        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Load shard to s3",
          States = {
            "Load shard to s3" = {
              Type     = "Task",
              Resource = "arn:aws:states:::lambda:invoke",
              Output   = "{% $states.result.Payload %}",
              Arguments = {
                FunctionName = module.ingestor_loader_lambda.lambda.arn,
                Payload      = "{% $states.input %}"
              },
              Retry = local.DefaultRetry,
              End   = true
            }
          }
        },
        Next = "Monitor loader output"
      },
      "Monitor loader output" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.DefaultRetry,
        Next  = "Scale down cluster"
      },
      "Scale down cluster" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Output   = "{% $states.input %}",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_scaler.arn
          Input = {
            min_capacity = 1,
            max_capacity = 32
          }
        },
        Retry = local.DefaultRetry,
        "Next" : "Map index to ES"
      }
      "Map index to ES" = {
        Type           = "Map",
        MaxConcurrency = 1
        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Index shard to ES",
          States = {
            "Index shard to ES" = {
              Type     = "Task",
              Resource = "arn:aws:states:::lambda:invoke",
              Output   = "{% $states.result.Payload %}",
              Arguments = {
                FunctionName = module.ingestor_indexer_lambda.lambda.arn,
                Payload      = "{% $states.input %}"
              },
              Retry = [
                {
                  ErrorEquals     = local.DefaultErrorEquals,
                  IntervalSeconds = 300,
                  # Don't try again yet!
                  MaxAttempts    = 1,
                  BackoffRate    = 2,
                  JitterStrategy = "FULL"
                }
              ],
              End = true
            }
          }
        },
        Next = "Remove documents"
      },
      "Remove documents" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.index_remover_lambda.lambda.arn,
          Payload = {
            pipeline_date = local.pipeline_date,
            index_date    = local.concepts_index_date
          }
        },
        Retry = local.DefaultRetry,
        Next  = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}
