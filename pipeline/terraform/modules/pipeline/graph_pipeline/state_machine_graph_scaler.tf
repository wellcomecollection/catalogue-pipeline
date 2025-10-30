module "catalogue_graph_scaler_state_machine" {
  source = "../../state_machine"
  name   = "graph-scaler-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONPath"
    Comment       = "Change the capacity of the serverless Neptune cluster and periodically check its status until new capacity applied."
    StartAt       = "Scale"
    States = {
      "Scale" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "OutputPath" : "$.Payload",
        "Parameters" : {
          "FunctionName" : module.graph_scaler_lambda.lambda.arn,
          "Payload.$" : "$"
        },
        Retry = local.state_function_default_retry,
        "Next" : "Wait 30 seconds"
      },
      "Wait 30 seconds" : {
        "Type" : "Wait",
        "Next" : "Check cluster status",
        "Seconds" : 30
      },
      "Check cluster status" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "OutputPath" : "$.Payload",
        "Parameters" : {
          "FunctionName" : module.graph_status_poller_lambda.lambda.arn
        },
        Retry = local.state_function_default_retry,
        "Next" : "Scale operation complete?"
      },
      "Scale operation complete?" : {
        "Type" : "Choice",
        "Choices" : [
          {
            "Variable" : "$.status",
            "StringEquals" : "available",
            "Next" : "Success"
          }
        ],
        "Default" : "Wait 30 seconds"
      },
      "Success" : {
        "Type" : "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.graph_scaler_lambda.lambda.arn,
    module.graph_status_poller_lambda.lambda.arn
  ]
}
