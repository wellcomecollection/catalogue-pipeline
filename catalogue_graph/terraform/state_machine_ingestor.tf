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

  cluster_min_scaled_down_capacity = 1
  cluster_min_scaled_up_capacity   = 24
  cluster_max_capacity             = 32

  s3_loader_concurrency = 3
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
      # Make sure the cluster is scaled up before we run queries against it. Neptune's automatic scaling system
      # is not responsive enough and often results in queries timing out and returning errors.
      "Scale up cluster" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Output   = "{% $states.input %}",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_scaler.arn
          Input = {
            min_capacity = local.cluster_min_scaled_up_capacity,
            max_capacity = local.cluster_max_capacity
          }
        },
        Retry = local.DefaultRetry,
        Next  = "Map load to s3"
      }
      # the next step is a state map that takes the json list output of the ingestor_trigger_lambda and maps it to a list of ingestor tasks
      "Map load to s3" = {
        Type           = "Map",
        MaxConcurrency = local.s3_loader_concurrency
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
        Catch = [
          {
            ErrorEquals = ["States.ALL"],
            Next        = "Clean up"
          }
        ],
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
        Catch = [
          {
            ErrorEquals = ["States.ALL"],
            Next        = "Clean up"
          }
        ],
        Next = "Scale down cluster"
      },
      # Keeping the cluster scaled up is expensive, so we scale it back down after we've extracted all data
      "Scale down cluster" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Output   = "{% $states.input %}",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_scaler.arn
          Input = {
            min_capacity = local.cluster_min_scaled_down_capacity,
            max_capacity = local.cluster_max_capacity
          }
        },
        Retry = local.DefaultRetry,
        "Next" : "Map index to ES"
      },
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
        Next = "Monitor indexer"
      },
      "Monitor indexer output" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke",
        Arguments = {
          FunctionName = module.ingestor_indexer_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Next = "Remove documents"
      },
      "Remove documents" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.input %}",
        Arguments = {
          FunctionName = module.ingestor_deletions_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.DefaultRetry,
        Next  = "Generate final report"
      },
      "Generate final report" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke",
        Arguments = {
          FunctionName = module.ingestor_reporter_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      },
      # Any steps between 'Scale up cluster' and 'Scale down cluster' can fail. If this happens, we need
      # to make sure the cluster still gets scaled down to prevent extra costs.
      "Clean up" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Output   = "{% $states.input %}",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_scaler.arn
          Input = {
            min_capacity = local.cluster_min_scaled_down_capacity,
            max_capacity = local.cluster_max_capacity
          }
        },
        Retry = local.DefaultRetry,
        Next : "Fail"
      },
      Fail = {
        Type  = "Fail",
        Error = "MainTaskError",
        Cause = "$.errorInfo.Cause"
      }
    }
  })
}
