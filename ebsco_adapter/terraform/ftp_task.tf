module "ftp_task" {
  source = "../../infrastructure/modules/task"

  task_name = "ebsco-adapter-ftp"

  image = "${aws_ecr_repository.ebsco_adapter.repository_url}:latest"

  environment = {
    FTP_SERVER       = aws_ssm_parameter.ebsco_adapter_ftp_server.value
    FTP_USERNAME     = aws_ssm_parameter.ebsco_adapter_ftp_username.value
    FTP_REMOTE_DIR   = aws_ssm_parameter.ebsco_adapter_ftp_remote_dir.value
    CUSTOMER_ID      = aws_ssm_parameter.ebsco_adapter_customer_id.value
    FTP_PASSWORD     = aws_ssm_parameter.ebsco_adapter_ftp_password.value
    OUTPUT_TOPIC_ARN = module.ebsco_adapter_output_topic.arn
    S3_BUCKET        = aws_s3_bucket.ebsco_adapter.bucket
    S3_PREFIX        = "prod"
  }

  cpu    = 2048
  memory = 4096
}

resource "aws_scheduler_schedule" "ftp_task_schedule" {
  name       = "ebsco-adapter-ftp-schedule"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "at(2024-08-05T10:30:00)"

  # Disable the schedule for now
  state = "ENABLED"

  target {
    arn      = aws_ecs_cluster.cluster.arn
    role_arn = aws_iam_role.eventbridge_task_scheduler.arn

    ecs_parameters {
      task_definition_arn = local.task_definition_arn_latest
      launch_type         = "FARGATE"

      network_configuration {
        assign_public_ip = false
        security_groups = [
          aws_security_group.egress.id,
          local.network_config.ec_privatelink_security_group_id
        ]
        subnets = local.network_config.subnets
      }
    }

    input = jsonencode({
      containerOverrides = [
        {
          name    = "ebsco-adapter-ftp"
          command = ["--scheduled-invoke"]
        }
      ]
    })

    retry_policy {
      maximum_retry_attempts = 3
    }
  }
}
