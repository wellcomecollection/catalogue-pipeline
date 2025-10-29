resource "aws_sfn_state_machine" "catalogue_graph_pipeline_monthly" {
  name     = "${local.namespace}-pipeline-monthly-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Transform raw concepts from external sources into nodes and edges and load them into the catalogue graph.",
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

resource "aws_sfn_state_machine" "catalogue_graph_pipeline_incremental" {
  name     = "${local.namespace}-pipeline-incremental-${var.pipeline_date}"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Load catalogue works and concepts them into the graph, and ingests into ES index.",
    StartAt       = "Open PIT"
    States = {
      "Open PIT" = {
        Type     = "Task",
        Resource = "arn:aws:states:::lambda:invoke",
        Arguments = {
          FunctionName = module.elasticsearch_pit_opener_lambda.lambda.arn,
          Payload      = "{% $states.context.Execution.Input %}"
        },
        Output = "{% $merge([$states.context.Execution.Input, $states.result.Payload ]) %}",
        Retry = local.state_function_default_retry,
        Next   = "Extractors"
      },
      "Extractors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_extractors_incremental.arn
          Input           = "{% $states.input %}"
        }
        Output = "{% $states.input %}",
        Next   = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_bulk_loaders_incremental.arn,
          Input           = "{% $states.input %}"
        }
        Output = "{% $states.input %}",
        Next   = "Graph removers"
      },
      "Graph removers" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_graph_removers_incremental.arn,
          Input           = "{% $states.input %}"
        }
        Output = "{% $states.input %}",
        Next   = "Ingestors"
      },
      "Ingestors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = aws_sfn_state_machine.catalogue_graph_ingestors.arn,
          Input           = "{% $states.input %}"
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })
}
