resource "aws_sfn_state_machine" "concepts_pipeline_monthly" {
  name     = "concepts-pipeline_monthly"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract raw concepts from external sources, transform them into nodes and edges, and load them into the graph",
    StartAt = "Extractors"
    States = {
      "Extractors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractors_monthly.arn
        }
        Next = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loaders_monthly.arn
        }
        Next = "Graph removers"
      },
      "Graph removers" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_removers.arn
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}

resource "aws_sfn_state_machine" "concepts_pipeline_daily" {
  name     = "concepts-pipeline_daily"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract concepts from catalogue works, load them into the graph, and ingests into ES index.",
    StartAt = "Extractors"
    States = {
      "Extractors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractors_daily.arn
        }
        Next = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loaders_daily.arn
        }
        Next = "Concepts ingestor"
      },
      "Concepts ingestor" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
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



