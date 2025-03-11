resource "aws_sfn_state_machine" "concepts_pipeline" {
  name     = "concepts-pipeline"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Build the catalogue graph and ingest concepts into ES",
    QueryLanguage = "JSONata"
    StartAt = "Extractors"
    States = {
      "Extractors" = {
        Type           = "Map",
        ItemProcessor  = {
          StartAt = "Extract nodes and edges from source",
          States = {
            "Extract nodes and edges from source" = {
              Type = "Task",
              Resource = "arn:aws:states:::states:startExecution.sync:2",
              Arguments = {
                StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractor.arn,
                Payload = "{% $states.input %}"
              },
              End = true
            }
          }
        } 
        Next = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type           = "Map",
        MaxConcurrency = 1 # ensures nodes and edges are loaded in the desired order
        ItemProcessor  = {
          StartAt = "Load Neptune graph from S3",
          States = {
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
    },
  })
}