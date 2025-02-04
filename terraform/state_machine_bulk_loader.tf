locals {
  bulk_loader_lambda = "${module.bulk_loader_lambda.lambda.arn}:${module.bulk_loader_lambda.lambda.version}"
  bulk_load_poller_lambda = "${module.bulk_load_poller_lambda.lambda.arn}:${module.bulk_load_poller_lambda.lambda.version}"
}
resource "aws_sfn_state_machine" "catalogue_graph_bulk_loader" {
  name     = "catalogue-graph-bulk-loader"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Trigger a Neptune bulk load from a file stored in S3 and periodically check the status of the bulk load until complete."
    StartAt       = "Trigger bulk load"
    States = {
      "Trigger bulk load" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "Output" : "{% $states.result.Payload %}",
        "Arguments" : {
          "FunctionName" : local.bulk_loader_lambda,
          "Payload" : "{% $states.input %}"
        },
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
        "Output" : "{% $states.result.Payload %}",
        "Arguments" : {
          "FunctionName" : local.bulk_load_poller_lambda,
          "Payload" : "{% $states.input %}"
        },
        "Next" : "Load complete?"
      },
      "Load complete?" : {
        "Type" : "Choice",
        "Output" : "{% $states.input %}",
        "Choices" : [
          {
            "Condition" : "{% $states.input.status = 'SUCCEEDED' %}",
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

