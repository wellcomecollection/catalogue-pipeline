module "catalogue_graph_ingestor_state_machine" {
  source = "../../state_machine"
  name   = "graph-pipeline-ingestor-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue works/concepts into the pipeline Elasticsearch cluster."
    StartAt       = "Run loader"
    States = {
      "Run loader" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Monitor loader"
      }
      "Monitor loader" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Run indexer"
      },
      "Run indexer" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_indexer_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Monitor indexer"
      }
      "Monitor indexer" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_indexer_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Next = "Should run deletions?"
      },
      "Should run deletions?" = {
        Type = "Choice"
        Choices = [
          {
            "Condition" : "{% $states.input.ingestor_type = 'concepts' %}",
            "Next" : "Run deletions"
          }
        ]
        Default = "Success"
      },
      "Run deletions" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_deletions_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.ingestor_loader_lambda.lambda.arn,
    module.ingestor_loader_monitor_lambda.lambda.arn,
    module.ingestor_indexer_lambda.lambda.arn,
    module.ingestor_indexer_monitor_lambda.lambda.arn,
    module.ingestor_deletions_lambda.lambda.arn
  ]
}

module "catalogue_graph_ingestor_state_machine_EXPERIMENTAL" {
  source = "../../state_machine"
  name   = "graph-pipeline-ingestor-${var.pipeline_date}-EXPERIMENTAL"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue works/concepts into the pipeline Elasticsearch cluster."
    StartAt       = "Run loader"
    States = {
      "Run loader" = {
        Type     = "Task"
        Resource = "arn:aws:states:::ecs:runTask.waitForTaskToken"
        Retry    = local.state_function_default_retry,
        Next     = "Monitor loader"
        Arguments = {
          Cluster        = aws_ecs_cluster.cluster.arn
          TaskDefinition = module.ingestor_loader_ecs_task.task_definition_arn
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
                Name    = "ingestor-loader-${var.pipeline_date}"
                Command = [
                  "/app/src/ingestor/steps/ingestor_loader.py", 
                  "--event", "{% $string($states.input) %}"
                ],
                Environment = [
                  {
                    "Name" : "TASK_TOKEN",
                    "Value" : "{% $states.context.Task.Token %}"
                  },
                  {
                    Name  = "CATALOGUE_GRAPH_S3_BUCKET"
                    Value = data.aws_s3_bucket.catalogue_graph_bucket.bucket
                  },
                  {
                    Name  = "INGESTOR_S3_PREFIX"
                    Value = "ingestor"
                  }
                ]
              }
            ]
          }
        }
      }
      "Monitor loader" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Output   = "{% $states.result.Payload %}",
        Arguments = {
          FunctionName = module.ingestor_loader_monitor_lambda.lambda.arn,
          Payload      = "{% $states.input %}"
        },
        Retry = local.state_function_default_retry,
        Next  = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.ingestor_loader_lambda.lambda.arn,
    module.ingestor_loader_monitor_lambda.lambda.arn,
    module.ingestor_indexer_lambda.lambda.arn,
    module.ingestor_indexer_monitor_lambda.lambda.arn,
    module.ingestor_deletions_lambda.lambda.arn
  ]
}

# TODO: Can we modularise this somehow, and the nasty local variables (like invokable_lambda_arns)?
data "aws_iam_policy_document" "run_ingestor_loader_ecs_task" {
  statement {
    effect = "Allow"
    actions = [
      "ecs:RunTask",
    ]
    resources = [
      "${local.ingestor_loader_task_definition_arn_latest}:*",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "iam:PassRole",
    ]
    resources = [
      module.ingestor_loader_ecs_task.task_execution_role_arn,
      module.ingestor_loader_ecs_task.task_role_arn,
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

resource "aws_iam_policy" "run_ingestor_loader_ecs_task" {
  policy = data.aws_iam_policy_document.run_ingestor_loader_ecs_task.json
}

resource "aws_iam_role_policy_attachment" "run_ingestor_loader_ecs_task" {
  role       = module.catalogue_graph_ingestor_state_machine_EXPERIMENTAL.state_machine_role_name
  policy_arn = aws_iam_policy.run_ingestor_loader_ecs_task.arn
}

