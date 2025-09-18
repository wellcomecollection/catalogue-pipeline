# State Machine
resource "aws_sfn_state_machine" "ebsco_transformer" {
  name       = var.name
  role_arn   = aws_iam_role.state_machine_role.arn
  definition = var.state_machine_definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.state_machine_logs.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }
}

# IAM Role for State Machine
resource "aws_iam_role" "state_machine_role" {
  name = "${var.name}-sfn-role"

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
  name        = "${var.name}-sfn-lambda-policy"
  description = "Allow state machine to invoke EBSCO adapter Lambda functions"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = var.invokable_lambda_arns
      }
    ]
  })
}

# IAM Policy to allow state:StartExecution on itself
resource "aws_iam_policy" "state_machine_self_start_execution_policy" {
  name        = "${var.name}-sfn-self-start-execution-policy"
  description = "Allow state machine to start executions of itself"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "states:StartExecution"
        ]
        Resource = aws_sfn_state_machine.ebsco_transformer.arn
      }
    ]
  })
}

# IAM Policy for State Machine CloudWatch Logging
resource "aws_iam_policy" "state_machine_logging_policy" {
  name        = "${var.name}-sfn-logging-policy"
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

# If a custom IAM policy is provided, attach it to the role
resource "aws_iam_policy" "custom_state_machine_policy" {
  count       = var.state_machine_iam_policy != null ? 1 : 0
  name        = "${var.name}-custom-sfn-policy"
  description = "Custom IAM policy for state machine"
  policy      = var.state_machine_iam_policy
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

resource "aws_iam_role_policy_attachment" "custom_state_machine_policy_attachment" {
  count      = var.state_machine_iam_policy != null ? 1 : 0
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.custom_state_machine_policy[0].arn
}

resource "aws_iam_role_policy_attachment" "state_machine_self_start_execution_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_self_start_execution_policy.arn
}

# CloudWatch Log Group for State Machine
resource "aws_cloudwatch_log_group" "state_machine_logs" {
  name              = "/aws/stepfunctions/${var.name}"
  retention_in_days = 14
}

# Data source for current AWS region
data "aws_region" "current" {}