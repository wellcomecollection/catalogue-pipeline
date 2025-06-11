data "aws_caller_identity" "current" {}

resource "aws_iam_role" "state_machine_execution_role" {
  name = "catalogue-graph-state-machine-execution-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Service = [
            "states.amazonaws.com",
            "scheduler.amazonaws.com"
          ]
        },
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_policy" "state_machine_policy" {
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"],
        Resource = "*"
      },
      {
        Effect = "Allow",
        Action = ["states:StartExecution", "states:RedriveExecution"],
        Resource = [
          aws_sfn_state_machine.catalogue_graph_extractor.arn,
          aws_sfn_state_machine.catalogue_graph_extractors.arn,
          aws_sfn_state_machine.catalogue_graph_extractors_monthly.arn,
          aws_sfn_state_machine.catalogue_graph_extractors_daily.arn,
          aws_sfn_state_machine.catalogue_graph_bulk_loader.arn,
          aws_sfn_state_machine.catalogue_graph_bulk_loaders.arn,
          aws_sfn_state_machine.catalogue_graph_bulk_loaders_monthly.arn,
          aws_sfn_state_machine.catalogue_graph_bulk_loaders_daily.arn,
          aws_sfn_state_machine.catalogue_graph_ingestor.arn,
          aws_sfn_state_machine.concepts_pipeline_monthly.arn,
          aws_sfn_state_machine.concepts_pipeline_daily.arn,
          aws_sfn_state_machine.catalogue_graph_scaler.arn
        ]
      },
      {
        Effect = "Allow",
        Action = ["lambda:InvokeFunction"],
        Resource = [
          module.bulk_loader_lambda.lambda.arn,
          module.bulk_load_poller_lambda.lambda.arn,
          module.ingestor_trigger_lambda.lambda.arn,
          module.ingestor_loader_lambda.lambda.arn,
          module.ingestor_indexer_lambda.lambda.arn,
          module.ingestor_loader_monitor_lambda.lambda.arn,
          module.ingestor_trigger_monitor_lambda.lambda.arn,
          module.graph_remover_lambda.lambda.arn,
          module.index_remover_lambda.lambda.arn,
          module.graph_scaler_lambda.lambda.arn,
          module.graph_status_poller_lambda.lambda.arn,
          module.concepts_pipeline_reporter_lambda.lambda.arn
        ]
      },
      {
        Effect   = "Allow",
        Action   = ["ecs:RunTask"],
        Resource = ["${local.extractor_task_definition_arn_latest}:*"]
      },
      {
        Effect = "Allow",
        Action = ["iam:PassRole"],
        Resource = [
          module.extractor_ecs_task.task_execution_role_arn,
          module.extractor_ecs_task.task_role_arn
        ]
      },
      # These EventBridge permissions are needed to allow state machines to perform the "startExecution.sync:2" action
      # (i.e. trigger another state machine and wait for it to complete)
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
