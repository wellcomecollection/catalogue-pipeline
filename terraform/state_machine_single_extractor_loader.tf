resource "aws_sfn_state_machine" "catalogue_graph_single_extract_load" {
  name     = "catalogue-graph-single-extract-load"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract nodes/edges from a single source and load them into the catalogue graph."
    StartAt = "Extract"
    States = {
      "Extract" = {
        Type     = "Task"
        Resource = module.extractor_lambda.lambda.arn
        Next     = "Load"
        "Parameters" : {
          "stream_destination" : "s3",
          "transformer_type.$" : "$$.Execution.Input.transformer_type",
          "entity_type.$" : "$$.Execution.Input.entity_type",
          "sample_size.$" : "$$.Execution.Input.sample_size"
        }
      }
      "Load" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          "Input.$" : "$$.Execution.Input",
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    },
  })
}
resource "aws_sfn_state_machine" "catalogue_graph_single_extract_load_ecs" {
  name     = "catalogue-graph-single-extract-load-ecs"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment       = "Extract nodes/edges from a single source and load them into the catalogue graph using an ECS task."
    QueryLanguage = "JSONata"
    StartAt       = "Extract"
    States = {
      Extract = {
        Type     = "Task"
        Resource = "arn:aws:states:::ecs:runTask.sync"
        Output   = "{% $states.input %}"
        Next     = "ShouldRunLoad"
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
                  "--stream-destination",
                  "{% $states.input.stream_destination %}"
                ]
              }
            ]
          }
        }
      },
      ShouldRunLoad = {
        Type   = "Choice"
        Output = "{% $states.input %}"
        Choices = [
          {
            # This is how you do null coalescing in JSONata 
            # https://github.com/jsonata-js/jsonata/issues/370#issuecomment-556995173
            Condition = "{% [$states.input.run_bulk_load, false][0] %}",
            Next      = "Load"
          }
        ]
        Default = "Success"
      },
      Load = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2"
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input           = "{% $states.input %}"
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    },
  })
}
