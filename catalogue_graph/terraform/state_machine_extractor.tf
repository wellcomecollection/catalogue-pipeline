resource "aws_sfn_state_machine" "catalogue_graph_extractor" {
  name     = "catalogue-graph-extractor"
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
        Retry    = local.DefaultRetry,
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
                aws_security_group.egress.id
              ]
            }
          },
          Overrides = {
            ContainerOverrides = [
              {
                Name = "catalogue-graph_extractor"
                Command = [
                  "--transformer-type",
                  "{% $states.input.transformer_type %}",
                  "--entity-type",
                  "{% $states.input.entity_type %}",
                  "{% $states.context.Execution.Input.window ? '--window-end' : '' %}",
                  "{% $states.context.Execution.Input.window ? $states.context.Execution.Input.window.end_time : '' %}",
                  "{% $states.context.Execution.Input.pit_id ? '--pit-id' : '' %}",
                  "{% $states.context.Execution.Input.pit_id ? $states.context.Execution.Input.pit_id : '' %}",
                  "--pipeline-date",
                  local.pipeline_date,
                ]
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
