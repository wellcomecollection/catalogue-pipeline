resource "aws_sfn_state_machine" "catalogue_graph_extractor" {
  name     = "${local.namespace}-extractor-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment       = "Run a single catalogue graph pipeline extractor task."
    QueryLanguage = "JSONata"
    StartAt       = "Extract"
    States = {
      Extract = {
        Type     = "Task"
        Resource = "arn:aws:states:::ecs:runTask.sync"
        Output   = "{% $states.input %}"
        Retry = local.state_function_default_retry,
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
                Name    = "catalogue-graph_extractor"
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
}
