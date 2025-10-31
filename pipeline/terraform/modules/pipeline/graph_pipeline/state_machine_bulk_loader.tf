module "catalogue_graph_bulk_loader_state_machine" {
  source = "../../state_machine"
  name   = "graph-bulk-loader-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONPath"
    Comment       = "Trigger a Neptune bulk load from an S3 file and periodically check its status until complete."
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
        Retry = local.state_function_default_retry,
        "Next" : "Wait 5 seconds"
      },
      "Wait 5 seconds" : {
        "Type" : "Wait",
        "Next" : "Check load status",
        "Seconds" : 5
      },
      "Check load status" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "OutputPath" : "$.Payload",
        "Parameters" : {
          "FunctionName" : module.bulk_load_poller_lambda.lambda.arn,
          "Payload.$" : "$"
        },
        Retry = local.state_function_default_retry,
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
        "Default" : "Wait 5 seconds"
      },
      "Success" : {
        "Type" : "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.bulk_loader_lambda.lambda.arn,
    module.bulk_load_poller_lambda.lambda.arn
  ]
}
