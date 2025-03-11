resource "aws_sfn_state_machine" "concepts_pipeline" {
  name     = "concepts-pipeline"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Build the catalogue graph and ingest concepts into ES",
    QueryLanguage = "JSONata"
    StartAt = "Build graph"
    States = {
      "Build graph" = {
        Type           = "Map",
        MaxConcurrency = 1 # ensures nodes and edges are extarcted and loaded in the desired order
        ItemProcessor  = {
          StartAt = "Extract nodes and edges from source",
          States = {
            "Extract nodes and edges from source" = {
              Type = "Task",
              Resource = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn,
                Payload = "{% $states.input %}"
                Input = {
                  stream_destination = "s3"
                }
              },
              Next = "Load Neptune graph from S3"
            },
            "Load Neptune graph from S3" = {
              Type = "Task",
              Resource = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loader.arn
                Payload = "{% $states.input %}"
              },
              End = true
            }
          }
        } 
        Next = "Concepts ingestor"
      },
      "Concepts ingestor" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_ingestor.arn,
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}