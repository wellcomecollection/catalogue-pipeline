locals {
  state_machine_definition = jsonencode({
    Comment = "Adapter pipeline (trigger, loader, publish event)"
    StartAt = "Run trigger"
    States  = {
      "Run trigger" = {
        Type     = "Task"
        Resource = module.trigger_lambda.lambda.arn
        Next     = "Run loader"
        Retry    = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      "Run loader" = {
        Type     = "Task"
        Resource = module.loader_lambda.lambda.arn
        Next     = "Should publish event?"
        Retry    = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      "Should publish event?" = {
        Type    = "Choice"
        Choices = [
          {
            Variable  = "$.changeset_ids[0]"
            IsPresent = true
            Next      = "Publish event"
          }
        ]
        Default = "Success"
      }
      "Publish event" = {
        Type       = "Task"
        Resource   = "arn:aws:states:::events:putEvents"
        Parameters = {
          Entries = [
            {
              Detail = {
                transformer_type  = var.namespace
                "job_id.$"        = "$.job_id"
                "changeset_ids.$" = "$.changeset_ids"
              }
              DetailType   = "${var.namespace}.adapter.completed"
              EventBusName = data.aws_cloudwatch_event_bus.event_bus.name
              Source       = "${var.namespace}.adapter"
            }
          ]
        }
        ResultPath = null
        Next       = "Success"
        Retry      = [
          {
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      Success = {
        Type = "Succeed"
      }
    }
  })
}

# IAM Role for State Machine
resource "aws_iam_role" "state_machine_role" {
  name = "${var.namespace}-adapter-state-machine-role"

  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
      }
    ]
  })
}

# Attach the policies to the role
resource "aws_iam_role_policy_attachment" "state_machine_lambda_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_lambda_policy.arn
}

resource "aws_iam_role_policy_attachment" "state_machine_logging_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_logging_policy.arn
}

resource "aws_iam_role_policy_attachment" "state_machine_eventbridge_put_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_eventbridge_put_policy.arn
}

# State Machine
resource "aws_sfn_state_machine" "state_machine" {
  name       = "${var.namespace}-adapter"
  role_arn   = aws_iam_role.state_machine_role.arn
  definition = local.state_machine_definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.state_machine_logs.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }
}

# CloudWatch Log Group for State Machine
resource "aws_cloudwatch_log_group" "state_machine_logs" {
  name              = "/aws/stepfunctions/${var.namespace}-adapter-pipeline"
  retention_in_days = 14
}
