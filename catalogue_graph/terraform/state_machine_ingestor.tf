resource "aws_sfn_state_machine" "catalogue_graph_ingestor" {
  name     = "catalogue-graph-ingestor"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue concept data into the pipeline cluster."
    StartAt       = "Trigger ingest"
    States = {
      "Trigger ingest" = {
        Type        = "Task"
        Resource    = "arn:aws:states:::lambda:invoke",
        Output      = "{% $states.result.Payload %}",
        Arguments   = {
          FunctionName  = module.ingestor_trigger_lambda.lambda.arn,
          Payload       = "{}"
        },
        Next        = "Map load to s3"
      },
      # the next step is a state map that takes the json list output of the ingestor_trigger_lambda and maps it to a list of ingestor tasks
      "Map load to s3" = {
        Type = "Map",
        MaxConcurrency = 50
        ItemProcessor = {
          ProcessorConfig = {
            Mode = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Load shard to s3",
          States = {
            "Load shard to s3" = {
              Type = "Task",
              Resource = "arn:aws:states:::lambda:invoke",
              Output = "{% $states.result.Payload %}",
              Arguments = {
                FunctionName = module.ingestor_loader_lambda.lambda.arn,
                Payload = "{% $states.input %}"
              },
              Retry = [
                {
                  ErrorEquals = [
                    "Lambda.ServiceException",
                    "Lambda.AWSLambdaException",
                    "Lambda.SdkClientException",
                    "Lambda.TooManyRequestsException"
                  ],
                  IntervalSeconds = 1,
                  MaxAttempts = 3,
                  BackoffRate = 2,
                  JitterStrategy = "FULL"
                }
              ],
              End = true
            }
          }
        },
        Next = "Map index to ES"
      },
      "Map index to ES" = {
        Type = "Map",
        MaxConcurrency = 1
        ItemProcessor = {
          ProcessorConfig = {
            Mode = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Index shard to ES",
          States = {
            "Index shard to ES" = {
              Type = "Task",
              Resource = "arn:aws:states:::lambda:invoke",
              Output = "{% $states.result.Payload %}",
              Arguments = {
                FunctionName = module.ingestor_indexer_lambda.lambda.arn,
                Payload = "{% $states.input %}"
              },
              Retry = [
                {
                  ErrorEquals = [
                    "Lambda.ServiceException",
                    "Lambda.AWSLambdaException",
                    "Lambda.SdkClientException",
                    "Lambda.TooManyRequestsException"
                  ],
                  IntervalSeconds = 300,
                  # Don't try again yet!
                  MaxAttempts = 1,
                  BackoffRate = 2,
                  JitterStrategy = "FULL"
                }
              ],
              End = true
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
