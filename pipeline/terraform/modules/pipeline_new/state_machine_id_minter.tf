locals {
  id_minter_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Invoke the id_minter Lambda"
    StartAt       = "ConstructEvent"
    States = {
      ConstructEvent = {
        Type = "Pass",
        # Replays may pass source_identifiers or an explicit window, plus a
        # job_id; scheduled runs derive the window end from scheduled_time -
        # 5min. Shape guards keep malformed input (e.g. window: null) from
        # reaching the Lambda as "no window", which would mint the full index.
        Output = trimspace(<<-EOT
          {% $merge([
            {'pipeline_date': '${var.pipeline_date}'},
            $type($states.input.source_identifiers) = 'array'
              ? {'source_identifiers': $states.input.source_identifiers}
              : {'window': $exists($states.input.window.end_time)
                  ? $states.input.window
                  : {'end_time': $fromMillis($toMillis($states.input.scheduled_time) - 300000)}},
            $type($states.input.job_id) = 'string' ? {'job_id': $states.input.job_id} : {}
          ]) %}
        EOT
        ),
        Next = "InvokeIdMinter"
      }
      InvokeIdMinter = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.id_minter_lambda.id_minter_lambda_arn
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

module "id_minter_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_id_minter"
  state_machine_definition = local.id_minter_state_machine_definition
  invokable_lambda_arns    = [module.id_minter_lambda.id_minter_lambda_arn]
}

module "id_minter_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.id_minter_state_machine.state_machine_arn
  alarm_name_prefix = "id-minter"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

# EventBridge Scheduler
resource "aws_scheduler_schedule" "id_minter_schedule" {
  name                = "id-minter-schedule-${var.pipeline_date}"
  schedule_expression = "cron(5,20,35,50 * * * ? *)"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.id_minter_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_id_minter_role.arn

    input = <<JSON
    {
      "scheduled_time": "<aws.scheduler.scheduled-time>"
    }
    JSON
  }

  state = var.enable_id_minter_schedule ? "ENABLED" : "DISABLED"
}

resource "aws_iam_role" "run_id_minter_role" {
  name = "run-id-minter-role-${var.pipeline_date}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "scheduler.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "run_id_minter_policy" {
  role = aws_iam_role.run_id_minter_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "states:StartExecution"
        Resource = module.id_minter_state_machine.state_machine_arn
      }
    ]
  })
}
