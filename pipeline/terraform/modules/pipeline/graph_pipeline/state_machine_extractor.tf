module "catalogue_graph_extractor_state_machine" {
  source = "../../state_machine"
  name   = "graph-extractor-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    Comment       = "Run a single catalogue graph pipeline extractor task."
    QueryLanguage = "JSONata"
    StartAt       = "Extract"
    States = {
      Extract = {
        Type     = "Task"
        Resource = "arn:aws:states:::ecs:runTask.sync"
        Output   = "{% $states.input %}"
        Retry    = local.state_function_default_retry,
        Next     = "Success"
        Arguments = {
          Cluster        = aws_ecs_cluster.cluster.arn
          TaskDefinition = module.extractor_ecs_task.task_definition_arn
          LaunchType     = "FARGATE"
          NetworkConfiguration = {
            AwsvpcConfiguration = {
              AssignPublicIp = "DISABLED"
              Subnets        = local.private_subnets
              SecurityGroups = [
                local.ec_privatelink_security_group_id,
                aws_security_group.graph_pipeline_security_group.id,
              ]
            }
          },
          Overrides = {
            ContainerOverrides = [
              {
                Name    = "graph-extractor-${var.pipeline_date}"
                Command = ["--event", "{% $string($states.input) %}"]
              }
            ]
          }
        }
      },
      Success = {
        Type = "Succeed"
      }
    },
  })

  state_machine_iam_policy = data.aws_iam_policy_document.run_extractor_ecs_task.json
}

data "aws_iam_policy_document" "run_extractor_ecs_task" {
  statement {
    effect = "Allow"
    actions = [
      "ecs:RunTask",
    ]
    resources = [
      "${local.extractor_task_definition_arn_latest}:*",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "iam:PassRole",
    ]
    resources = [
      module.extractor_ecs_task.task_execution_role_arn,
      module.extractor_ecs_task.task_role_arn,
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]

    resources = [
      "*",
    ]
  }
}
