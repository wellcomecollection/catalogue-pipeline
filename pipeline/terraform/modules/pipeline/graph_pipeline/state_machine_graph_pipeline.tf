module "catalogue_graph_pipeline_monthly_state_machine" {
  source = "../../state_machine"
  name   = "graph-pipeline-monthly-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    Comment = "Transform raw concepts from external sources into nodes and edges and load them into the catalogue graph.",
    StartAt = "Extractors"
    States = {
      "Extractors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = module.catalogue_graph_extractors_monthly_state_machine.state_machine_arn
        }
        Next = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = module.catalogue_graph_bulk_loaders_monthly_state_machine.state_machine_arn
        }
        Next = "Graph removers"
      },
      "Graph removers" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Parameters = {
          StateMachineArn = module.catalogue_graph_removers_monthly_state_machine.state_machine_arn
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_extractors_monthly_state_machine.state_machine_arn,
    module.catalogue_graph_bulk_loaders_monthly_state_machine.state_machine_arn,
    module.catalogue_graph_removers_monthly_state_machine.state_machine_arn
  ]
}

module "catalogue_graph_pipeline_incremental_state_machine" {
  source = "../../state_machine"
  name   = "graph-pipeline-incremental-${var.pipeline_date}"

  state_machine_definition = jsonencode({
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
        Retry  = local.state_function_default_retry,
        Next   = "Extractors"
      },
      "Extractors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = module.catalogue_graph_extractors_incremental_state_machine.state_machine_arn
          Input           = "{% $states.input %}"
        }
        Output = "{% $states.input %}",
        Next   = "Bulk loaders"
      },
      "Bulk loaders" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = module.catalogue_graph_bulk_loaders_incremental_state_machine.state_machine_arn,
          Input           = "{% $states.input %}"
        }
        Output = "{% $states.input %}",
        Next   = "Graph removers"
      },
      "Graph removers" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = module.catalogue_graph_removers_incremental_state_machine.state_machine_arn
          Input           = "{% $states.input %}"
        }
        Output = "{% $states.input %}",
        Next   = "Ingestors"
      },
      "Ingestors" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2",
        Arguments = {
          StateMachineArn = module.catalogue_graph_ingestors_state_machine.state_machine_arn,
          Input           = "{% $states.input %}"
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_lambda_arns = [
    module.elasticsearch_pit_opener_lambda.lambda.arn
  ]

  invokable_state_machine_arns = [
    module.catalogue_graph_extractors_incremental_state_machine.state_machine_arn,
    module.catalogue_graph_bulk_loaders_incremental_state_machine.state_machine_arn,
    module.catalogue_graph_removers_incremental_state_machine.state_machine_arn,
    module.catalogue_graph_ingestors_state_machine.state_machine_arn,
  ]
}
