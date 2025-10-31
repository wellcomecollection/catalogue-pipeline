module "catalogue_graph_ingestor_state_machine" {
  source = "../../state_machine"
  name   = "graph-pipeline-ingestor-${var.pipeline_date}"

  state_machine_definition = jsonencode({
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
        Next  = "Monitor indexer"
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

  invokable_lambda_arns = [
    module.ingestor_loader_lambda.lambda.arn,
    module.ingestor_loader_monitor_lambda.lambda.arn,
    module.ingestor_indexer_lambda.lambda.arn,
    module.ingestor_indexer_monitor_lambda.lambda.arn,
    module.ingestor_deletions_lambda.lambda.arn
  ]
}

