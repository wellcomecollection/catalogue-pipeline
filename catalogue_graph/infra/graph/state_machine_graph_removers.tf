resource "aws_sfn_state_machine" "catalogue_graph_removers" {
  name     = "catalogue-graph-removers"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Remove unused source concepts and corresponding edges"
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
