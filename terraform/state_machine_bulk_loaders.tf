resource "aws_sfn_state_machine" "catalogue_graph_bulk_loaders" {
  name     = "catalogue-graph-bulk-loaders"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment       = "Extract raw concepts, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt       = "Load ${var.state_machine_inputs[0].label}"
    States        = merge(tomap({
      for index, task_input in var.state_machine_inputs :
      "Load ${task_input.label}" => {
        Type       = "Task"
        Resource   = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
          Input           = {
            "transformer_type" = task_input.transformer_type,
            "entity_type"      = task_input.entity_type
          }
        }
        Next = index == length(var.state_machine_inputs) - 1 ? "Success" : "Load ${var.state_machine_inputs[index + 1].label}"
      }
    }), {
      Success = {
        Type = "Succeed"
      }
    })

  })
}

