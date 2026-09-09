locals {
  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Axiell to Folio sync: invoke the sync Lambda on adapter completion"
    StartAt       = "Run sync"
    States = {
      "Run sync" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.sync_lambda.lambda.arn
          # Every field the sync step accepts is mapped through. Anything omitted
          # here is silently dropped by the time it reaches the Lambda — Pydantic
          # ignores unknown keys — so a run would fall back to the env default
          # with no indication the event asked for something else.
          Payload = {
            changeset_ids    = "{% $states.input.detail.changeset_ids %}"
            job_id           = "{% $states.input.detail.job_id %}"
            transformer_type = "{% $exists($states.input.detail.transformer_type) ? $states.input.detail.transformer_type : null %}"
            sample_limit     = "{% $exists($states.input.detail.sample_limit) ? $states.input.detail.sample_limit : null %}"
            dry_run          = "{% $exists($states.input.detail.dry_run) ? $states.input.detail.dry_run : ${var.dry_run_default} %}"
            hard_delete      = "{% $exists($states.input.detail.hard_delete) ? $states.input.detail.hard_delete : null %}"
            # Resolved here rather than deferred to the Lambda's FOLIO_TARGET env
            # var, and for the same reason dry_run is: this path is the automated
            # every-15-minutes pipeline, and it must not follow a temporary switch
            # made for hand-driven testing. scripts/folio_dev_session.sh flips that
            # env var to point *direct* invocations at the sandbox; baking the
            # value in at apply time keeps the scheduled runs on production
            # regardless. An event that names a target still wins.
            folio_target = "{% $exists($states.input.detail.folio_target) ? $states.input.detail.folio_target : '${var.folio_default_target}' %}"
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
