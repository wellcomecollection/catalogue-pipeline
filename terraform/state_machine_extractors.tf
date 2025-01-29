resource "aws_sfn_state_machine" "catalogue_graph_extractors" {
  name     = "catalogue-graph-extractors"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract raw concepts from all sources, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt = "Trigger extractors"
    States  = {
      "Trigger extractors" = {
        Type     = "Parallel"
        Branches = flatten([
          for index, task_input in var.state_machine_inputs : {
            StartAt = "Extract ${task_input.label}"
            States  = {
              "Extract ${task_input.label}" = {
                Type       = "Task"
                Resource   = module.extractor_lambda.lambda.arn
                Parameters = {
                  "transformer_type"   = task_input.transformer_type,
                  "entity_type"        = task_input.entity_type,
                  "stream_destination" = "s3"
                }
                End = true
              }
            }
          }
        ])
        Next = "Success"
      },
      "Success" : {
        "Type" : "Succeed"
      }
    }
  })
}
