module "minter_id_generator_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_minter_id_generator"
  state_machine_definition = local.state_machine_definition
  invokable_lambda_arns    = [module.id_minter_lambda.id_generator_lambda_arn]
}

locals {
  state_machine_definition = jsonencode({
    QueryLanguage = "JSONPath"
    Comment       = "Runs the ids generator Lambda to ensure a pool of pre-generated IDs is available for the id_minter"
    StartAt       = "Top up ids"
    States = {
      "Top up ids" : {
        "Type" : "Task",
        "Resource" : "arn:aws:states:::lambda:invoke",
        "OutputPath" : "$.Payload",
        "Parameters" : {
          "FunctionName" : module.id_minter_lambda.id_generator_lambda_arn,
          "Payload.$" : "$"
        },
        "Next" : "Success"
      },
      "Success" : {
        "Type" : "Succeed"
      }
    }
  })
}


module "minter_id_generator_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.minter_id_generator_state_machine.state_machine_arn
  alarm_name_prefix = "id-generator"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

resource "aws_scheduler_schedule" "minter_id_generator_schedule" {
  name = "minter-id-generator-schedule-${var.pipeline_date}"

  schedule_expression = "cron(0 5 ? * MON-FRI *)" # Monday to Friday at 5am UTC

  flexible_time_window {
    mode = "FLEXIBLE"
    maximum_window_in_minutes = 15
  }

  target {
    arn      = module.minter_id_generator_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_minter_id_generator_role.arn
  }
}

resource "aws_iam_role" "run_minter_id_generator_role" {
  name = "run-minter-id-generator-role-${var.pipeline_date}"

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

resource "aws_iam_role_policy" "run_minter_id_generator_policy" {
  role = aws_iam_role.run_minter_id_generator_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "states:StartExecution"
        Resource = module.minter_id_generator_state_machine.state_machine_arn
      }
    ]
  })
}