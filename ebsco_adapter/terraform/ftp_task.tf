module "ftp_task" {
  source = "../../infrastructure/modules/task"

  task_name = "ebsco-adapter-ftp"

  image = "${aws_ecr_repository.ebsco_adapter.repository_url}:latest"

  environment = {
    FTP_SERVER        = aws_ssm_parameter.ebsco_adapter_ftp_server.value
    FTP_USERNAME      = aws_ssm_parameter.ebsco_adapter_ftp_username.value
    FTP_REMOTE_DIR    = aws_ssm_parameter.ebsco_adapter_ftp_remote_dir.value
    CUSTOMER_ID       = aws_ssm_parameter.ebsco_adapter_customer_id.value
    FTP_PASSWORD      = aws_ssm_parameter.ebsco_adapter_ftp_password.value
    OUTPUT_TOPIC_ARN  = module.ebsco_adapter_output_topic.arn
    REINDEX_TOPIC_ARN = local.reindexer_topic_arn
    S3_BUCKET         = aws_s3_bucket.ebsco_adapter.bucket
    S3_PREFIX         = "prod"
  }

  cpu    = 2048
  memory = 4096
}

resource "aws_cloudwatch_event_rule" "reindex_rule" {
  name        = "ebsco-adapter-reindex-rule"
  description = "Rule to trigger custom reindex event for EBSCO adapter"
  event_pattern = jsonencode({
    "source" : ["weco.pipeline.reindex"],
    "detail" : {
      "ReindexTargets" : ["ebsco"]
    }
  })
}

resource "aws_cloudwatch_event_target" "ftp_task_reindex_target" {
  arn      = aws_ecs_cluster.cluster.arn
  rule     = aws_cloudwatch_event_rule.reindex_rule.name
  role_arn = aws_iam_role.eventbridge_task_scheduler.arn

  ecs_target {
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
        command = ["--process-type", "reindex-full"]
      }
    ]
  })
}

resource "aws_cloudwatch_event_rule" "schedule_rule" {
  name        = "ebsco-adapter-schedule-rule"
  description = "Rule to schedule and manually trigger EBSCO adapter"

  # Invoke the rule every day
  schedule_expression = "rate(1 day)"

  # Also allow manual invocation
  event_pattern = jsonencode({
    "source" : ["weco.pipeline.adapter"],
    "detail" : {
      "InvokeTargets" : ["ebsco"]
    }
  })
}

resource "aws_cloudwatch_event_target" "ftp_task_schedule_target" {
  arn      = aws_ecs_cluster.cluster.arn
  rule     = aws_cloudwatch_event_rule.schedule_rule.name
  role_arn = aws_iam_role.eventbridge_task_scheduler.arn

  ecs_target {
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
        command = ["--process-type", "scheduled"]
      }
    ]
  })
}
