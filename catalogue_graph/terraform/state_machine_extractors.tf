resource "aws_sfn_state_machine" "catalogue_graph_extractors_monthly" {
  name     = "catalogue-graph-extractors_monthly"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract raw concepts from external sources, transform them into nodes and edges, and load load them into the graph"
    StartAt = "Extract ${local.concepts_pipeline_inputs_monthly[0].label}"

    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_monthly :
      "Extract ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn
          Input = {
            "stream_destination" : "s3",
            "transformer_type.$" : "$$.Execution.Input.transformer_type",
            "entity_type.$" : "$$.Execution.Input.entity_type",
            "sample_size.$" : "$$.Execution.Input.sample_size"
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

resource "aws_sfn_state_machine" "catalogue_graph_extractors_daily" {
  name     = "catalogue-graph-extractors_daily"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract raw concepts from all sources, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt = "Extract ${local.concepts_pipeline_inputs_daily[0].label}"

    States = merge(tomap({
      for index, task_input in local.concepts_pipeline_inputs_daily :
      "Extract ${task_input.label}" => {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn
          Input = {
            "stream_destination" : "s3",
            "transformer_type.$" : "$$.Execution.Input.transformer_type",
            "entity_type.$" : "$$.Execution.Input.entity_type",
            "sample_size.$" : "$$.Execution.Input.sample_size"
          }
        }
        Next = index == length(local.concepts_pipeline_inputs_daily) - 1 ? "Success" : "Extract ${local.concepts_pipeline_inputs_daily[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })
}
