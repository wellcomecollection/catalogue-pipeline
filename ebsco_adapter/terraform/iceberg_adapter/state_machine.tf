# State Machine Definition
locals {
  state_machine_definition = jsonencode({
    Comment = "EBSCO Adapter Pipeline - Trigger -> Loader -> Transformer"
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
      TransitionStep = {
        Type     = "Choice"
        Choices  = [
          {
            Variable = "$.is_processed"
            BooleanEquals = true
            Next         = "Success"
          }
        ]
        Default = "LoaderStep"
      }
      LoaderStep = {
        Type     = "Task"
        Resource = module.loader_lambda.lambda.arn
        Next     = "TransformerStep"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      TransformerStep = {
        Type     = "Task"
        Resource = module.transformer_lambda.lambda.arn
        End      = true
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
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
  name = "ebsco-adapter-state-machine-role"

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

# IAM Policy for State Machine to invoke Lambda functions
resource "aws_iam_policy" "state_machine_lambda_policy" {
  name        = "ebsco-adapter-state-machine-lambda-policy"
  description = "Allow state machine to invoke EBSCO adapter Lambda functions"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = [
          module.trigger_lambda.lambda.arn,
          module.loader_lambda.lambda.arn,
          module.transformer_lambda.lambda.arn
        ]
      }
    ]
  })
}

# IAM Policy for State Machine CloudWatch Logging
resource "aws_iam_policy" "state_machine_logging_policy" {
  name        = "ebsco-adapter-state-machine-logging-policy"
  description = "Allow state machine to write logs to CloudWatch"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogDelivery",
          "logs:GetLogDelivery",
          "logs:UpdateLogDelivery",
          "logs:DeleteLogDelivery",
          "logs:ListLogDeliveries",
          "logs:PutResourcePolicy",
          "logs:DescribeResourcePolicies",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
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

# State Machine
resource "aws_sfn_state_machine" "ebsco_adapter_pipeline" {
  name       = "ebsco-adapter-pipeline"
  role_arn   = aws_iam_role.state_machine_role.arn
  definition = local.state_machine_definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.state_machine_logs.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }

  tags = {
    Environment = "production"
    Service     = "ebsco-adapter"
  }
}

# CloudWatch Log Group for State Machine
resource "aws_cloudwatch_log_group" "state_machine_logs" {
  name              = "/aws/stepfunctions/ebsco-adapter-pipeline"
  retention_in_days = 14

  tags = {
    Environment = "production"
    Service     = "ebsco-adapter"
  }
}

# IAM Role for EventBridge to trigger State Machine
resource "aws_iam_role" "eventbridge_state_machine_role" {
  name = "ebsco-adapter-eventbridge-state-machine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
      }
    ]
  })
}

# IAM Policy for EventBridge to start State Machine executions
resource "aws_iam_policy" "eventbridge_state_machine_policy" {
  name        = "ebsco-adapter-eventbridge-state-machine-policy"
  description = "Allow EventBridge to start EBSCO adapter state machine executions"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "states:StartExecution"
        ]
        Resource = [
          aws_sfn_state_machine.ebsco_adapter_pipeline.arn
        ]
      }
    ]
  })
}

# Attach the policy to the EventBridge role
resource "aws_iam_role_policy_attachment" "eventbridge_state_machine_policy_attachment" {
  role       = aws_iam_role.eventbridge_state_machine_role.name
  policy_arn = aws_iam_policy.eventbridge_state_machine_policy.arn
}

# EventBridge Rule for Daily Schedule (initially disabled)
resource "aws_cloudwatch_event_rule" "daily_schedule" {
  name                = "ebsco-adapter-daily-schedule"
  description         = "Trigger EBSCO adapter pipeline daily at 2 AM UTC"
  schedule_expression = "cron(0 2 * * ? *)"
  state               = "DISABLED"

  tags = {
    Environment = "production"
    Service     = "ebsco-adapter"
  }
}

# EventBridge Target to trigger State Machine
resource "aws_cloudwatch_event_target" "state_machine_target" {
  rule      = aws_cloudwatch_event_rule.daily_schedule.name
  target_id = "EbscoAdapterStateMachineTarget"
  arn       = aws_sfn_state_machine.ebsco_adapter_pipeline.arn
  role_arn  = aws_iam_role.eventbridge_state_machine_role.arn

  input = jsonencode({
    job_id = "daily-scheduled-run"
    source = "eventbridge-schedule"
  })
}

# Outputs
output "state_machine_arn" {
  description = "ARN of the EBSCO adapter state machine"
  value       = aws_sfn_state_machine.ebsco_adapter_pipeline.arn
}

output "daily_schedule_rule_name" {
  description = "Name of the daily schedule EventBridge rule"
  value       = aws_cloudwatch_event_rule.daily_schedule.name
}

output "state_machine_execution_url" {
  description = "URL to view state machine executions in AWS Console"
  value       = "https://console.aws.amazon.com/states/home?region=${data.aws_region.current.name}#/statemachines/view/${aws_sfn_state_machine.ebsco_adapter_pipeline.arn}"
}

# Data source for current AWS region
data "aws_region" "current" {}
