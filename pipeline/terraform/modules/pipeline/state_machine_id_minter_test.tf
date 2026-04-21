locals {
  # rds_test_config = {
  #   subnet_group      = local.infra_critical.rds_subnet_group_name
  #   security_group_id = local.infra_critical.rds_test_access_security_group_id
  # }
  
  id_minter_test_vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      # local.rds_test_config.security_group_id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  id_minter_test_env_vars = {
    LOG_LEVEL                   = "INFO"
    RDS_MAX_CONNECTIONS         = local.id_minter_task_max_connections
    ES_SOURCE_INDEX_DATE_SUFFIX = "2026-01-12"
    ES_TARGET_INDEX_DATE_SUFFIX = "2026-03-06"
    S3_BUCKET                   = "wellcomecollection-platform-id-minter"
    S3_PREFIX                   = "test"
  }

  # rds_test_master_secret_name = regex(
  #   "arn:aws:secretsmanager:[^:]+:[^:]+:secret:(.+)-.{6}$",
  #   local.infra_critical.rds_test_master_user_secret_arn
  # )[0]

  id_minter_test_secret_env_vars = merge(
    # {
    #   RDS_PRIMARY_HOST = "rds/identifiers-test-serverless/endpoint"
    #   RDS_REPLICA_HOST = "rds/identifiers-test-serverless/reader_endpoint"
    #   RDS_PORT         = "rds/identifiers-test-serverless/port"
    #   RDS_USERNAME     = "${local.rds_test_master_secret_name}:username"
    #   RDS_PASSWORD     = "${local.rds_test_master_secret_name}:password"
    # },
    module.elastic.pipeline_storage_es_service_secrets["id_minter"],
  )

  id_minter_test_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Invoke the id_minter Lambda"
    StartAt       = "ConstructEvent"
    States = {
      ConstructEvent = {
        Type = "Pass",
        Output = {
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
          FunctionName = module.id_minter_test.id_minter_lambda_arn
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

module "id_minter_test" {
  source = "./id_minter"

  pipeline_date         = var.pipeline_date
  namespace             = "test"
  include_id_generator  = false

  vpc_config            = local.id_minter_test_vpc_config
  env_vars              = local.id_minter_test_env_vars
  secret_env_vars       = local.id_minter_test_secret_env_vars
  alarm_topic_arn       = local.monitoring_infra["chatbot_topic_arn"]
}

module "id_minter_test_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_id_minter_test"
  state_machine_definition = local.id_minter_test_state_machine_definition
  invokable_lambda_arns    = [module.id_minter_test.id_minter_lambda_arn]
}

module "id_minter_test_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.id_minter_test_state_machine.state_machine_arn
  alarm_name_prefix = "id-minter-test"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

# EventBridge Scheduler
resource "aws_scheduler_schedule" "id_minter_test_schedule" {
  name                = "id-minter-test-schedule-${var.pipeline_date}"
  schedule_expression = "cron(5,20,35,50 * * * ? *)"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.id_minter_test_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_id_minter_test_role.arn

    input = <<JSON
    {
      "scheduled_time": "<aws.scheduler.scheduled-time>"
    }
    JSON
  }

  state = "DISABLED"
}

resource "aws_iam_role" "run_id_minter_test_role" {
  name = "run-id-minter-test-role-${var.pipeline_date}"

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

resource "aws_iam_role_policy" "run_id_minter_test_policy" {
  role = aws_iam_role.run_id_minter_test_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = module.id_minter_test_state_machine.state_machine_arn
    }]
  })
}
