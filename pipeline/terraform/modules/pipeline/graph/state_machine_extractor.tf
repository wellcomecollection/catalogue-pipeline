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
          Cluster        = var.ecs_cluster_arn
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
                Name = "graph-extractor-${var.pipeline_date}"
                Command = [
                  "/app/src/extractor.py",
                  "--event", "{% $string($states.input) %}",
                ],
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

  policies_to_attach = {
    "extractor_ecs_task_invoke_policy" = module.extractor_ecs_task.invoke_policy_document
  }
}