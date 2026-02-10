# State Machine Definition
locals {
  state_machine_definition = jsonencode({
    Comment = "FOLIO Adapter Pipeline - Trigger -> Loader -> PublishEvent"
    StartAt = "TriggerStep"
    States = {
      TriggerStep = {
        Type     = "Task"
        Resource = module.trigger_lambda.lambda.arn
        Next     = "LoaderStep"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      LoaderStep = {
        Type     = "Task"
        Resource = module.loader_lambda.lambda.arn
        Next     = "PublishDecision"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      PublishDecision = {
        Type = "Choice"
        Choices = [
          {
            Variable  = "$.changeset_ids[0]"
            IsPresent = true
            Next      = "PublishEvent"
          }
        ]
        Default = "Success"
      }
      PublishEvent = {
        Type     = "Task"
        Resource = "arn:aws:states:::events:putEvents"
        Parameters = {
          Entries = [
            {
              Detail = {
                transformer_type  = "folio"
                "job_id.$"        = "$.job_id"
                "changeset_ids.$" = "$.changeset_ids"
              }
              DetailType   = "folio.adapter.completed"
              EventBusName = data.aws_cloudwatch_event_bus.event_bus.name
              Source       = "folio.adapter"
            }
          ]
        }
        ResultPath = null
        Next       = "Success"
        Retry = [
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
  name = "folio-adapter-state-machine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
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
  name       = "folio-adapter"
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
  name              = "/aws/stepfunctions/folio-adapter-pipeline"
  retention_in_days = 14
}

# IAM Role for EventBridge to trigger State Machine
resource "aws_iam_role" "eventbridge_state_machine_role" {
  name = "folio-adapter-eventbridge-state-machine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = [
            "states.amazonaws.com",
            "scheduler.amazonaws.com"
          ]
        },
        Action = "sts:AssumeRole"
      }
    ]
  })
}

# Attach the policy to the EventBridge role
resource "aws_iam_role_policy_attachment" "eventbridge_state_machine_policy_attachment" {
  role       = aws_iam_role.eventbridge_state_machine_role.name
  policy_arn = aws_iam_policy.eventbridge_state_machine_policy.arn
}

# Run every 15 minutes
resource "aws_scheduler_schedule" "folio_adapter_15_minute_run" {
  name = "folio_adapter_15_minute_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "rate(15 minutes)"
  # Enable this to turn on regular harvest
  state = "DISABLED"

  target {
    arn      = aws_sfn_state_machine.state_machine.arn
    role_arn = aws_iam_role.eventbridge_state_machine_role.arn
  }
}
