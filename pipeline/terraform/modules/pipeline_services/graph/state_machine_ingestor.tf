module "catalogue_graph_ingestor_state_machine" {
  source = "../../state_machine"
  name   = "graph-pipeline-ingestor-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Ingest catalogue works/concepts into the pipeline Elasticsearch cluster."
    StartAt       = "Run loader"
    States = {
      "Run loader" = {
        Type             = "Task"
        Resource         = "arn:aws:states:::ecs:runTask.waitForTaskToken"
        TimeoutSeconds   = local.ecs_task_token_timeout_seconds
        HeartbeatSeconds = local.ecs_task_token_heartbeat_seconds
        Retry            = concat(local.state_function_default_retry, local.transient_neptune_retry),
        Next             = "Run indexer"
        Arguments = {
          Cluster        = var.ecs_cluster_arn
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
                Name = "ingestor-loader-${var.pipeline_date}"
                Command = [
                  "/app/src/ingestor/steps/ingestor_loader.py",
                  "--event", "{% $string($states.input) %}",
                  "--task-token", "{% $states.context.Task.Token %}"
                ],
              }
            ]
          }
        }
      },
      "Run indexer" = {
        Type             = "Task"
        Resource         = "arn:aws:states:::ecs:runTask.waitForTaskToken"
        TimeoutSeconds   = local.ecs_task_token_timeout_seconds
        HeartbeatSeconds = local.ecs_task_token_heartbeat_seconds
        Retry            = local.state_function_default_retry,
        Next             = "Should run deletions?"
        Arguments = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = module.ingestor_indexer_ecs_task.task_definition_arn
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
                Name = "ingestor-indexer-${var.pipeline_date}"
                Command = [
                  "/app/src/ingestor/steps/ingestor_indexer.py",
                  "--event", "{% $string($states.input) %}",
                  "--task-token", "{% $states.context.Task.Token %}"
                ],
              }
            ]
          }
        }
      }
      "Should run deletions?" = {
        Type = "Choice"
        Choices = [
          {
            # Reconciliation deletions only make sense for a windowed run, whose graph removers have
            # written the window's parquet of removed ids. Skip them when the event carries `ids`
            # (a targeted by-id re-ingest): the loader/indexer treat `ids` as "ingest these", but the
            # deletions step treats the same `ids` as "delete these" (see ingestor_deletions.py), so
            # without this guard a by-id run would index those ids and then immediately delete them
            # from the live index. Skip full-mode runs (no window) too: nothing writes their parquet.
            # The indexer emits `ids` as JSON null, which JSONata counts as one item, so null is
            # tested explicitly.
            "Condition" : "{% $states.input.ingestor_type in ['concepts', 'images'] and $states.input.window != null and ($states.input.ids = null or $count($states.input.ids) = 0) %}",
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
          FunctionName = module.ingestor_deletions_lambda.lambda_arn,
          Payload      = "{% $states.input %}"
        },
        # Deletions run after the loader and indexer have committed, so a transient S3 or
        # Elasticsearch error inside the function gets a retry rather than failing the window.
        Retry = concat(local.state_function_default_retry, [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 30
          MaxAttempts     = 2
          BackoffRate     = 2
        }]),
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.ingestor_deletions_lambda.lambda_arn
  ]

  policies_to_attach = {
    "ingestor_loader_ecs_task_invoke_policy"  = module.ingestor_loader_ecs_task.invoke_policy_document
    "ingestor_indexer_ecs_task_invoke_policy" = module.ingestor_indexer_ecs_task.invoke_policy_document
  }
}