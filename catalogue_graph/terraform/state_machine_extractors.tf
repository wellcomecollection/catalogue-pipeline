resource "aws_sfn_state_machine" "catalogue_graph_extractors" {
  name     = "catalogue-graph-extractors"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract raw concepts from all sources, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt = "Extract ${var.state_machine_inputs[0].label}"

    States = merge(tomap({
      for index, task_input in var.state_machine_inputs :
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
        Next = index == length(var.state_machine_inputs) - 1 ? "Success" : "Extract ${var.state_machine_inputs[index + 1].label}"
      }
      }), {
      Success = {
        Type = "Succeed"
      }
    })
  })
}
