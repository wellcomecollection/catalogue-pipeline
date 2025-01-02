data "aws_caller_identity" "current" {}

resource "aws_iam_role" "state_machine_execution_role" {
  name               = "catalogue-graph-state-machine-execution-role"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect    = "Allow",
        Principal = {
          Service = "states.amazonaws.com"
        },
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_policy" "state_machine_policy" {
  policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"],
        Resource = "*"
      },
      {
        Effect   = "Allow",
        Action   = ["lambda:InvokeFunction"],
        Resource = "*"
      },
      {
        Effect   = "Allow",
        Action   = ["states:StartExecution", "states:DescribeExecution", "states:StopExecution"],
        Resource = "*"
      },
      {
        Effect   = "Allow",
        Action   = ["events:PutTargets", "events:PutRule", "events:DescribeRule"],
        Resource = "arn:aws:events:eu-west-1:${data.aws_caller_identity.current.account_id}:rule/StepFunctions*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sfn_policy_attachment" {
  role       = aws_iam_role.state_machine_execution_role.name
  policy_arn = aws_iam_policy.state_machine_policy.arn
}
