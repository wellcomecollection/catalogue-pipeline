locals {
  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Axiell → FOLIO sync: invoke the sync Lambda on adapter completion"
    StartAt       = "Run sync"
    States = {
      "Run sync" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.sync_lambda.lambda.arn
          Payload = {
            changeset_ids    = "{% $states.input.detail.changeset_ids %}"
            job_id           = "{% $states.input.detail.job_id %}"
            transformer_type = "{% $states.input.detail.transformer_type %}"
            dry_run          = "{% $exists($states.input.detail.dry_run) ? $states.input.detail.dry_run : ${var.dry_run_default} %}"
          }
        }
        Output = "{% $states.result.Payload %}"
        Next   = "Success"
        Retry = [
          {
            ErrorEquals = [
              "Lambda.ServiceException",
              "Lambda.AWSLambdaException",
              "Lambda.SdkClientException",
              "States.TaskFailed",
            ]
            IntervalSeconds = 2
            MaxAttempts     = var.max_sync_retries
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

# IAM role for the state machine
resource "aws_iam_role" "state_machine_role" {
  name = "${var.namespace}-adapter-state-machine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = { Service = "states.amazonaws.com" }
      }
    ]
  })
}

data "aws_iam_policy_document" "state_machine" {
  statement {
    effect    = "Allow"
    actions   = ["lambda:InvokeFunction"]
    resources = [module.sync_lambda.lambda.arn, "${module.sync_lambda.lambda.arn}:*"]
  }

  # Log delivery actions operate at the account/service level — must be "*".
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogDelivery",
      "logs:GetLogDelivery",
      "logs:UpdateLogDelivery",
      "logs:DeleteLogDelivery",
      "logs:ListLogDeliveries",
      "logs:PutResourcePolicy",
      "logs:DescribeResourcePolicies",
      "logs:DescribeLogGroups",
    ]
    resources = ["*"]
  }

  statement {
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [aws_cloudwatch_log_group.state_machine.arn, "${aws_cloudwatch_log_group.state_machine.arn}:*"]
  }
}

resource "aws_iam_role_policy" "state_machine" {
  name   = "${var.namespace}-adapter-state-machine"
  role   = aws_iam_role.state_machine_role.id
  policy = data.aws_iam_policy_document.state_machine.json
}

resource "aws_cloudwatch_log_group" "state_machine" {
  name              = "/aws/stepfunctions/${var.namespace}-adapter-pipeline"
  retention_in_days = 14
}

resource "aws_sfn_state_machine" "state_machine" {
  name       = "${var.namespace}-adapter"
  role_arn   = aws_iam_role.state_machine_role.arn
  definition = local.state_machine_definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.state_machine.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }
}
