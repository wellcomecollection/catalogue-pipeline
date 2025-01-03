resource "aws_sfn_state_machine" "catalogue_graph_pipeline" {
  name     = "catalogue-graph-pipeline"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract all concepts and load them into the catalogue graph."
    StartAt = "Extractors"
    States  = {
      "Extractors" = {
        Type       = "Task"
        Resource   = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractors.arn
        }
        Next = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type       = "Task"
        Resource   = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loaders.arn
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    },
  })
}
