resource "aws_sfn_state_machine" "catalogue_graph_extractors_monthly" {
  name     = "${local.namespace}-extractors-monthly-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Transform raw concepts from external sources into nodes and edges and stream them into an S3 bucket."
    StartAt = "Extract ${local.concepts_pipeline_inputs_monthly[0].label}"

    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_monthly :
      "Extract ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn
          Input = {
            "transformer_type" : task_input.transformer_type,
            "entity_type" : task_input.entity_type,
            "pipeline_date" : var.pipeline_date,
            "sample_size" : contains(keys(task_input), "sample_size") ? task_input.sample_size : null,
          }
        }
        Next = index == length(local.concepts_pipeline_inputs_monthly) - 1 ? "Success" : "Extract ${local.concepts_pipeline_inputs_monthly[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })
}

resource "aws_sfn_state_machine" "catalogue_graph_extractors_incremental" {
  name     = "${local.namespace}-extractors-incremental-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Extract catalogue works/concepts, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt       = "Extractors"

    States = {
      "Extractors" = {
        Type           = "Map",
        Items          = local.concepts_pipeline_inputs_incremental
        MaxConcurrency = 10

        ItemSelector = {
          "transformer_type" : "{% $states.context.Map.Item.Value.transformer_type %}",
          "entity_type" : "{% $states.context.Map.Item.Value.entity_type %}",
          "pipeline_date" : "{% $states.context.Execution.Input.pipeline_date %}",
          "window" : "{% $states.context.Execution.Input.window ? $states.context.Execution.Input.window : null %}",
          "pit_id" : "{% $states.context.Execution.Input.pit_id ? $states.context.Execution.Input.pit_id : null %}",
        }

        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED",
            ExecutionType = "STANDARD"
          },
          StartAt = "Run extractor",
          States = {
            "Run extractor" = {
              Type     = "Task",
              Resource = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn
                Input           = "{% $states.input %}"
              },
              Retry = local.state_function_default_retry,
              End   = true
            }
          }
        },
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}


