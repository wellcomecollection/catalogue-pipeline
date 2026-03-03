module "minter_ids_generator_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_minter_ids_generator"
  state_machine_definition = local.state_machine_definition
  invokable_lambda_arns    = [module.ids_generator_lambda.lambda_arn]
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
          "FunctionName" : module.ids_generator_lambda.lambda_arn,
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


module "minter_ids_generator_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.minter_ids_generator_state_machine.state_machine_arn
  alarm_name_prefix = "ids-generator"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

resource "aws_scheduler_schedule" "minter_ids_generator_schedule" {
  name = "minter-ids-generator-schedule-${var.pipeline_date}"
  
  schedule_expression = "cron(0 5 ? * MON-FRI *)"  # Monday to Friday at 5am UTC
  
  flexible_time_window {
    mode = "FLEXIBLE"
  }
  
  target {
    arn      = module.minter_ids_generator_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_minter_ids_generator_role.arn
  }
}

resource "aws_iam_role" "run_minter_ids_generator_role" {
  name = "run-minter-ids-generator-role-${var.pipeline_date}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { 
          Service = "scheduler.amazonaws.com" 
        }
        Action    = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "run_minter_ids_generator_policy" {
  role = aws_iam_role.run_minter_ids_generator_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "states:StartExecution"
        Resource = module.minter_ids_generator_state_machine.state_machine_arn
      }
    ]
  })
}