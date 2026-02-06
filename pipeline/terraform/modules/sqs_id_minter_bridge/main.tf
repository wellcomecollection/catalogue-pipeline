locals {
  # Extract just the transformer type (e.g., "calm", "sierra") from the topic name
  # Topic names follow the pattern: catalogue-{date}_transformer_{type}_output
  topic_names = {
    for arn in var.sns_topic_arns :
    arn => regex("transformer_([^_]+)_output$", element(split(":", arn), length(split(":", arn)) - 1))[0]
  }

  # State machine definition with JSONata transformation
  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Transform SQS batch messages and invoke id_minter Lambda"
    StartAt       = "TransformMessages"
    States = {
      TransformMessages = {
        Type    = "Pass"
        Comment = "Parse SQS messages and extract source identifiers from SNS envelopes"
        Output = {
          # Parse each SQS message body (SNS envelope JSON) and extract the Message field
          # $states.input is the array of SQS records from EventBridge Pipe
          "sourceIdentifiers" = "{% $map($states.input, function($r) { $parse($r.body).Message }) %}"
          # Use the execution name as the jobId
          "jobId" = "{% $states.context.Execution.Name %}"
        }
        Next = "InvokeIdMinter"
      }
      InvokeIdMinter = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = var.lambda_arn
          Payload      = "{% $states.input %}"
        }
        Output = "{% $states.result.Payload %}"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
        End = true
      }
    }
  })
}

# State Machine
module "state_machine" {
  source = "../state_machine"

  name                     = var.name
  state_machine_definition = local.state_machine_definition
  invokable_lambda_arns    = [var.lambda_arn]
}

# EventBridge Pipes - one per SNS topic
module "eventbridge_pipe" {
  for_each = local.topic_names
  source   = "../eventbridge_pipe"

  name              = "${var.name}-${each.value}"
  sns_topic_arn     = each.key
  state_machine_arn = module.state_machine.state_machine_arn

  batch_size                         = var.batch_size
  maximum_batching_window_in_seconds = var.maximum_batching_window_in_seconds
  queue_visibility_timeout_seconds   = var.queue_visibility_timeout_seconds
  dlq_alarm_arn                      = var.dlq_alarm_arn
  enabled                            = var.enabled
}
