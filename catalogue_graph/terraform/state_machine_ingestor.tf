locals {
  DefaultErrorEquals = [
    "Lambda.ServiceException",
    "Lambda.AWSLambdaException",
    "Lambda.SdkClientException",
    "Lambda.TooManyRequestsException",
    "Ecs.ServerException",
    "Ecs.ThrottlingException",
    "Ecs.TaskFailedToStartException",
    "Ecs.CannotPullContainerErrorException",
    "Ecs.ContainerRuntimeTimeoutErrorException",
    "Ecs.EssentialContainerExited"
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
    Comment       = "Ingest catalogue works/concepts into the pipeline Elasticsearch cluster."
    StartAt       = "Run loader"
    States        = {
      "Run loader" = {
        Type      = "Task",
        Resource  = "arn:aws:states:::lambda:invoke",
        Output    = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.DefaultRetry,
        Next  = "Monitor loader"
      }
      "Monitor loader" = {
        Type      = "Task",
        Resource  = "arn:aws:states:::lambda:invoke",
        Output    = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.DefaultRetry,
        Next  = "Run indexer"
      },
      "Run indexer" = {
        Type      = "Task",
        Resource  = "arn:aws:states:::lambda:invoke",
        Output    = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_indexer_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = [
          {
            ErrorEquals     = local.DefaultErrorEquals,
            IntervalSeconds = 300,
            # Don't try again yet!
            MaxAttempts     = 1,
            BackoffRate     = 2,
            JitterStrategy  = "FULL"
          }
        ],
        Next = "Monitor indexer"
      }
      "Monitor indexer" = {
        Type      = "Task"
        Resource  = "arn:aws:states:::lambda:invoke",
        Output    = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_indexer_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}


resource "aws_sfn_state_machine" "catalogue_graph_ingestors" {
  name     = "catalogue-graph-ingestors"
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
          "index_date" : "{% $states.context.Execution.Input.index_date %}",
          "window" : "{% $states.context.Execution.Input.window ? $states.context.Execution.Input.window : null %}",
        }

        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Run ingestor",
          States  = {
            "Run ingestor" = {
              Type      = "Task",
              Resource  = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = aws_sfn_state_machine.catalogue_graph_ingestor.arn
                Input           = "{% $states.input %}"
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

