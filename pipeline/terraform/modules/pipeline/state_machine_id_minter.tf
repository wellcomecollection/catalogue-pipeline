locals {
  id_minter_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Invoke the id_minter Lambda"
    StartAt       = "ConstructEvent"
    States = {
      ConstructEvent = {
        "Type" : "Pass",
        "Output" : {
          "pipeline_date" : var.pipeline_date,
          # window end time is 5 minutes before the scheduled time
          "window" : {
            "end_time" : "{% $fromMillis($toMillis($states.input.scheduled_time) - 300000) %}"
          }
        },
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

  state = "DISABLED"
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
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = module.id_minter_state_machine.state_machine_arn
    }]
  })
}
