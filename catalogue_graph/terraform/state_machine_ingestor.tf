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
        Retry = local.DefaultRetry,
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
        Retry = local.DefaultRetry,
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
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}
