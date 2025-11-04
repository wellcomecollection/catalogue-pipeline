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
          Cluster        = aws_ecs_cluster.pipeline_cluster.arn
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
                  "--event", "{% $string($states.input) %}",
                  "--task-token", "{% $states.context.Task.Token %}"
                ],
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

  policies_to_attach = {
    "ingestor_loader_ecs_task_invoke_policy" = module.ingestor_loader_ecs_task.invoke_policy_document
  }
}