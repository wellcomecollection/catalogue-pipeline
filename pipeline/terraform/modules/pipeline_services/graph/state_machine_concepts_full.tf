# Label matching votes on a concept's type across the works it extracts, so a 15-minute
# window can match differently from a full run. A nightly full extract re-asserts the
# whole-catalogue vote, the remover drops the edges it no longer produces, and a full
# concepts ingest carries the result into the concepts index.
# See wellcomecollection/platform#6739.
locals {
  concepts_full_extractor_input = {
    transformer_type = "catalogue_concepts"
    pipeline_date    = var.pipeline_date
    graph_date       = var.graph_date
    index_dates      = var.index_dates
    sample_size      = null
  }
}

module "catalogue_graph_concepts_full_state_machine" {
  source = "../../state_machine"
  name   = "graph-concepts-full-${var.pipeline_date}"

  state_machine_definition = jsonencode({
    Comment = "Re-extract all catalogue concepts, load the edges, remove stale HAS_SOURCE_CONCEPT edges and re-ingest concepts."
    StartAt = "Extract concepts"
    States = {
      "Extract concepts" = {
        Type = "Parallel"
        Branches = [
          {
            StartAt = "Extract concept nodes"
            States = {
              "Extract concept nodes" = {
                Type     = "Task"
                Resource = "arn:aws:states:::states:startExecution.sync:2"
                Parameters = {
                  StateMachineArn = module.catalogue_graph_extractor_state_machine.state_machine_arn
                  Input           = merge(local.concepts_full_extractor_input, { entity_type = "nodes" })
                }
                End = true
              }
            }
          },
          {
            StartAt = "Extract concept edges"
            States = {
              "Extract concept edges" = {
                Type     = "Task"
                Resource = "arn:aws:states:::states:startExecution.sync:2"
                Parameters = {
                  StateMachineArn = module.catalogue_graph_extractor_state_machine.state_machine_arn
                  Input           = merge(local.concepts_full_extractor_input, { entity_type = "edges" })
                }
                End = true
              }
            }
          }
        ]
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
        Next  = "Ingest concepts"
      },
      "Ingest concepts" = {
        Type     = "Task"
        Resource = "arn:aws:states:::states:startExecution.sync:2"
        Parameters = {
          StateMachineArn = module.catalogue_graph_ingestor_state_machine.state_machine_arn
          Input = {
            ingestor_type = "concepts"
            pipeline_date = var.pipeline_date
            graph_date    = var.graph_date
            index_dates   = var.index_dates
          }
        }
        Next = "Success"
      },
      Success = {
        Type = "Succeed"
      }
    }
  })

  invokable_state_machine_arns = [
    module.catalogue_graph_extractor_state_machine.state_machine_arn,
    module.catalogue_graph_bulk_loader_state_machine.state_machine_arn,
    module.catalogue_graph_ingestor_state_machine.state_machine_arn,
  ]

  invokable_lambda_arns = [
    module.graph_remover_incremental_lambda.lambda_arn
  ]
}

module "graph_concepts_full_state_machine_alarms" {
  source = "../../state_machine_alarms"

  state_machine_arn = module.catalogue_graph_concepts_full_state_machine.state_machine_arn
  alarm_name_prefix = "graph-concepts-full-run"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
  }
}
