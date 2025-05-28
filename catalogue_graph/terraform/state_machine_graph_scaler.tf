resource "aws_sfn_state_machine" "catalogue_graph_scaler" {
  name     = "catalogue-graph-scaler"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONPath"
    Comment       = "Change the capacity of the serverless Neptune cluster and periodically check the status of the cluster until new capacity applied."
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
        Retry = local.DefaultRetry,
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
        Retry = local.DefaultRetry,
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
}
