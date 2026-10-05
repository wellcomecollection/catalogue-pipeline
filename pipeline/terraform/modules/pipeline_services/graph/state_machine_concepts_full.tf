# Label matching votes on a concept's type across the works it extracts, so a 15-minute
# window can match differently from a full run. A nightly full edge extract re-asserts the
# whole-catalogue vote and the remover drops the edges it no longer produces.
# See wellcomecollection/platform#6739.
module "catalogue_graph_concepts_full_state_machine" {
  source = "../../state_machine"
  name   = "graph-concepts-full-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    Comment = "Re-extract and load all catalogue concept edges, then remove stale HAS_SOURCE_CONCEPT edges."
    StartAt = "Extract concept edges"
    States = {
      "Extract concept edges" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2"
        Parameters = {
          StateMachineArn = module.catalogue_graph_extractor_state_machine.state_machine_arn
          Input = {
            transformer_type = "catalogue_concepts"
            entity_type      = "edges"
            pipeline_date    = var.pipeline_date
            graph_date       = var.graph_date
            sample_size      = null
          }
        }
        Next = "Load concept edges"
      },
      "Load concept edges" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2"
        Parameters = {
          StateMachineArn = module.catalogue_graph_bulk_loader_state_machine.state_machine_arn
          Input = {
            transformer_type       = "catalogue_concepts"
            entity_type            = "edges"
            pipeline_date          = var.pipeline_date
            graph_date             = var.graph_date
            insert_error_threshold = 1
          }
        }
        Next = "Remove stale concept edges"
      },
      "Remove stale concept edges" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = module.graph_remover_incremental_lambda.lambda_arn
          Payload = {
            transformer_type = "catalogue_concepts"
            entity_type      = "edges"
            pipeline_date    = var.pipeline_date
            graph_date       = var.graph_date
          }
        }
        Retry = concat(local.state_function_default_retry, local.transient_neptune_retry)
        Next  = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_extractor_state_machine.state_machine_arn,
    module.catalogue_graph_bulk_loader_state_machine.state_machine_arn,
  ]

  invokable_lambda_arns = [
    module.graph_remover_incremental_lambda.lambda_arn
  ]
}
