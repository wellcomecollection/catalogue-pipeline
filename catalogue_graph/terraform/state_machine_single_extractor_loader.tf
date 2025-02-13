resource "aws_sfn_state_machine" "catalogue_graph_single_extract_load" {
  name     = "catalogue-graph-single-extract-load"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract nodes/edges from a single source and load them into the catalogue graph."
    StartAt = "Extract"
    States = {
      "Extract" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Next     = "Load"
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn
          Input = {
            "stream_destination" : "s3",
            "transformer_type.$" : "$$.Execution.Input.transformer_type",
            "entity_type.$" : "$$.Execution.Input.entity_type",
            "sample_size.$" : "$$.Execution.Input.sample_size"
          }
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
