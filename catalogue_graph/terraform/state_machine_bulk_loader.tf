resource "aws_sfn_state_machine" "catalogue_graph_bulk_loader" {
  name     = "catalogue-graph-bulk-loader"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONPath"
    Comment       = "Trigger a Neptune bulk load from a file stored in S3 and periodically check the status of the bulk load until complete."
    StartAt       = "Trigger bulk load"
    States = {
      "Trigger bulk load" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "OutputPath" : "$.Payload",
        "Parameters" : {
          "FunctionName" : module.bulk_loader_lambda.lambda.arn,
          "Payload.$" : "$"
        },
        Retry = local.DefaultRetry,
        "Next" : "Wait 30 seconds"
      },
      "Wait 30 seconds" : {
        "Type" : "Wait",
        "Next" : "Check load status",
        "Seconds" : 30
      },
      "Check load status" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "OutputPath" : "$.Payload",
        "Parameters" : {
          "FunctionName" : module.bulk_load_poller_lambda.lambda.arn,
          "Payload.$" : "$"
        },
        Retry = local.DefaultRetry,
        "Next" : "Load complete?"
      },
      "Load complete?" : {
        "Type" : "Choice",
        "Choices" : [
          {
            "Variable" : "$.status",
            "StringEquals" : "SUCCEEDED",
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
