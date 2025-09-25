# IAM role assumed by EventBridge to start executions of a state machine
resource "aws_iam_role" "eventbridge_invoke_role" {
  name = "${var.name}-eventbridge-invoke-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = { Service = "events.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_policy" "eventbridge_invoke_policy" {
  name        = "${var.name}-eventbridge-invoke-policy"
  description = "Allow EventBridge rule to StartExecution on the state machine"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["states:StartExecution"]
        Resource = var.state_machine_arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eventbridge_invoke_attachment" {
  role       = aws_iam_role.eventbridge_invoke_role.name
  policy_arn = aws_iam_policy.eventbridge_invoke_policy.arn
}

resource "aws_cloudwatch_event_rule" "trigger_rule" {
  name           = "${var.name}-trigger"
  description    = "Trigger ${var.state_machine_arn} when matching events received"
  event_bus_name = var.event_bus_name
  event_pattern  = jsonencode(var.event_pattern)
}

resource "aws_cloudwatch_event_target" "trigger_target" {
  rule      = aws_cloudwatch_event_rule.trigger_rule.name
  arn       = var.state_machine_arn
  role_arn  = aws_iam_role.eventbridge_invoke_role.arn
  event_bus_name = var.event_bus_name

  dynamic "input_transformer" {
    for_each = var.input_template != null ? [1] : []
    content {
      input_template = var.input_template
      input_paths    = var.input_paths
    }
  }
}

output "rule_name" {
  description = "Name of the EventBridge rule"
  value       = aws_cloudwatch_event_rule.trigger_rule.name
}

output "invoke_role_arn" {
  description = "ARN of the IAM role assumed by EventBridge"
  value       = aws_iam_role.eventbridge_invoke_role.arn
}
