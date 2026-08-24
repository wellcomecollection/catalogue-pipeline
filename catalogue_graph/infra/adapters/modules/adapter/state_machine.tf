locals {
  # When reconciliation is enabled, the loader routes through a "Run reconcile"
  # state so deletion facts are durably recorded before the completed event
  # fires; when item enrichment is enabled, it routes through "Run enrichment"
  # so both the bib and item changesets exist by then. Otherwise the loader
  # goes straight to publish. No adapter currently enables both, but the
  # chain composes: reconcile runs first, then enrichment.
  loader_next    = var.enable_reconciliation ? "Should reconcile?" : local.reconcile_next
  reconcile_next = var.enable_item_enrichment ? "Should enrich?" : "Should publish event?"

  # With published tracking, both the publish and the quiet no-publish paths end
  # in "Mark published", which stamps the covered windows so the trigger resumes
  # from the last published window. The quiet path matters: quiet windows are
  # success rows too, and skipping them would stall the published cursor.
  post_publish_next = local.published_tracking ? "Mark published" : "Success"

  # The loader response names the success windows whose changesets it carries
  # (covered_window_keys); "Mark published" stamps exactly those rows. The
  # enrichment response drops the field, so carry it past that state here.
  covered_keys_passthrough_output = merge([
    for _ in range(local.published_tracking ? 1 : 0) : {
      Output = "{% $merge([$states.result, {'covered_window_keys': $states.input.covered_window_keys}]) %}"
    }
  ]...)

  # Carry the items changeset for provenance only when enrichment runs.
  # The `merge([for ...]...)` pattern yields an empty map when disabled, avoiding
  # the type-unification error a `? : {}` conditional would raise.
  publish_event_detail = merge(
    {
      transformer_type = var.namespace
      job_id           = "{% $states.input.job_id %}"
      changeset_ids    = "{% $states.input.changeset_ids %}"
    },
    merge([
      for _ in range(var.enable_item_enrichment ? 1 : 0) : {
        items_changeset_ids = "{% $states.input.items_changeset_ids %}"
      }
    ]...)
  )

  reconcile_states = merge([
    for _ in range(var.enable_reconciliation ? 1 : 0) : {
      "Should reconcile?" = {
        Type = "Choice"
        Choices = [
          {
            Condition = "{% $exists($states.input.changeset_ids[0]) %}"
            Next      = "Run reconcile"
          }
        ]
        Default = local.reconcile_next
      }
      "Run reconcile" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = one(module.reconcile_lambda[*].lambda.arn)
          # The loader response carries no adapter identity, so inject it.
          Payload = "{% $merge([$states.input, {'adapter_type': '${var.namespace}'}]) %}"
        }
        # The loader response continues downstream unchanged; the reconcile
        # response (fact and mapping counts) stays visible in the execution
        # history.
        Output = "{% $states.input %}"
        Next   = local.reconcile_next
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          },
          {
            # Iceberg optimistic-concurrency commit conflicts; re-runs dedupe
            # on deterministic fact ids, so retrying is safe.
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 5
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
        # No Catch: a failed reconcile must fail the execution before
        # "Publish event" and "Mark published". A guid change never reappears
        # in a later changeset, so publishing without its facts would lose
        # the deletion; leaving the windows unstamped makes the next trigger
        # re-cover them and retry.
      }
    }
  ]...)

  enrichment_states = merge([
    for _ in range(var.enable_item_enrichment ? 1 : 0) : {
      "Should enrich?" = {
        Type = "Choice"
        Choices = [
          {
            Condition = "{% $exists($states.input.changeset_ids[0]) %}"
            Next      = "Run enrichment"
          }
        ]
        Default = "Should publish event?"
      }
      "Run enrichment" = merge({
        Type           = "Task"
        Resource       = "arn:aws:states:::ecs:runTask.waitForTaskToken"
        TimeoutSeconds = var.task_token_timeout_seconds
        Next           = "Should publish event?"
        Retry = [
          {
            # A timed-out token means the task is gone, so retrying only waits
            # again. Fail now; the next scheduled run re-covers the window.
            ErrorEquals = ["States.Timeout"]
            MaxAttempts = 0
          },
          {
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 30
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
        Arguments = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = one(module.enrichment_ecs_task[*].task_definition_arn)
          LaunchType     = "FARGATE"
          NetworkConfiguration = {
            AwsvpcConfiguration = {
              AssignPublicIp = "DISABLED"
              Subnets        = var.subnets
              SecurityGroups = var.security_group_ids
            }
          }
          Overrides = {
            ContainerOverrides = [
              {
                Name = "${var.namespace}-adapter-enrichment"
                Command = [
                  "-m", "adapters.steps.${local.steps_namespace}.folio_enrich",
                  "--event", "{% $string($states.input) %}",
                  "--task-token", "{% $states.context.Task.Token %}"
                ]
              }
            ]
          }
        }
      }, local.covered_keys_passthrough_output)
    }
  ]...)

  # The oai_pmh loader has two modes. A scheduled run carries only
  # {adapter_type} and falls through to the trigger, which produces a window
  # event. An operator starting an execution with {adapter_type, ids} instead
  # routes to id mode, which fetches those ids directly. Both converge on the
  # same publish path, so a recovery reaches the transformer unaided.
  id_mode_states = merge([
    for _ in range(local.id_mode_enabled ? 1 : 0) : {
      "Which mode?" = {
        Type = "Choice"
        Choices = [
          {
            # Tests for the field, not its first element. An explicitly supplied
            # but empty list still routes to id mode, where the event validator
            # rejects it, rather than falling through to a window harvest the
            # caller never asked for.
            Condition = "{% $exists($states.input.ids) %}"
            Next      = "Prepare id run"
          }
        ]
        Default = "Run trigger"
      }
      "Prepare id run" = {
        Type = "Pass"
        # A window run takes its job id from the trigger. An id run has no
        # trigger, so mint one from the execution start time in the same
        # YYYYMMDDTHHMM shape as trigger.generate_job_id, prefixed so recovery
        # runs stand out in the transformer logs and cannot collide with a
        # harvest job id.
        Output = "{% $merge([$states.input, {'job_id': 'idload-' & $substring($replace($replace($states.context.Execution.StartTime, '-', ''), ':', ''), 0, 13)}]) %}"
        Next   = "Run id loader"
      }
      # Same task definition and module as "Run loader"; a separate state purely
      # so the retry policy can differ. Do not merge the two. A window-mode retry
      # is cheap and idempotent because already-successful windows are skipped,
      # but id mode keeps no progress state, so any retry re-fetches every id
      # from scratch with a politeness delay on each. At the per-run ceiling that
      # is hours of duplicated load on a source already known to be flaky, so
      # there are no automatic retries at all: failed ids come back in the
      # report for a deliberate, smaller second run.
      "Run id loader" = {
        Type           = "Task"
        Resource       = "arn:aws:states:::ecs:runTask.waitForTaskToken"
        TimeoutSeconds = var.task_token_timeout_seconds
        Next           = local.loader_next
        Retry = [
          {
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 30
            MaxAttempts     = 0
            BackoffRate     = 2.0
          }
        ]
        Arguments = {
          Cluster        = var.ecs_cluster_arn
          TaskDefinition = module.loader_ecs_task.task_definition_arn
          LaunchType     = "FARGATE"
          NetworkConfiguration = {
            AwsvpcConfiguration = {
              AssignPublicIp = "DISABLED"
              Subnets        = var.subnets
              SecurityGroups = var.security_group_ids
            }
          }
          Overrides = {
            ContainerOverrides = [
              {
                Name = "${var.namespace}-adapter-loader"
                Command = [
                  "-m", "adapters.steps.${local.steps_namespace}.loader",
                  "--event", "{% $string($states.input) %}",
                  "--task-token", "{% $states.context.Task.Token %}"
                ]
              }
            ]
          }
        }
      }
    }
  ]...)

  base_states = {
    "Run trigger" = {
      Type     = "Task"
      Resource = "arn:aws:states:::lambda:invoke"
      Arguments = {
        FunctionName = module.trigger_lambda.lambda.arn
        Payload      = "{% $states.input %}"
      }
      Output = "{% $states.result.Payload %}"
      Next   = "Run loader"
      Retry = [
        {
          ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
          IntervalSeconds = 2
          MaxAttempts     = 3
          BackoffRate     = 2.0
        }
      ]
    }
    "Run loader" = {
      Type           = "Task"
      Resource       = "arn:aws:states:::ecs:runTask.waitForTaskToken"
      TimeoutSeconds = var.task_token_timeout_seconds
      Next           = local.loader_next
      Retry = [
        {
          # A timed-out token means the task is gone, so retrying only waits
          # again. Fail now; the next scheduled run re-covers the window.
          ErrorEquals = ["States.Timeout"]
          MaxAttempts = 0
        },
        {
          ErrorEquals     = ["States.ALL"]
          IntervalSeconds = 30
          MaxAttempts     = 3
          BackoffRate     = 2.0
        }
      ]
      Arguments = {
        Cluster        = var.ecs_cluster_arn
        TaskDefinition = module.loader_ecs_task.task_definition_arn
        LaunchType     = "FARGATE"
        NetworkConfiguration = {
          AwsvpcConfiguration = {
            AssignPublicIp = "DISABLED"
            Subnets        = var.subnets
            SecurityGroups = var.security_group_ids
          }
        }
        Overrides = {
          ContainerOverrides = [
            {
              Name = "${var.namespace}-adapter-loader"
              Command = [
                "-m", "adapters.steps.${local.steps_namespace}.loader",
                "--event", "{% $string($states.input) %}",
                "--task-token", "{% $states.context.Task.Token %}"
              ]
            }
          ]
        }
      }
    }
    "Should publish event?" = {
      Type = "Choice"
      Choices = [
        {
          Condition = "{% $exists($states.input.changeset_ids[0]) %}"
          Next      = "Publish event"
        }
      ]
      Default = local.post_publish_next
    }
    "Publish event" = {
      Type     = "Task"
      Resource = "arn:aws:states:::events:putEvents"
      Arguments = {
        Entries = [
          {
            Detail       = local.publish_event_detail
            DetailType   = "${var.namespace}.adapter.completed"
            EventBusName = data.aws_cloudwatch_event_bus.event_bus.name
            Source       = "${var.namespace}.adapter"
          }
        ]
      }
      Output = "{% $states.input %}"
      Next   = local.post_publish_next
      Retry = [
        {
          ErrorEquals     = ["States.ALL"]
          IntervalSeconds = 2
          MaxAttempts     = 3
          BackoffRate     = 2.0
        }
      ]
    }
    Success = {
      Type = "Succeed"
    }
  }

  mark_published_states = merge([
    for _ in range(local.published_tracking ? 1 : 0) : {
      "Mark published" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = one(module.mark_published_lambda[*].lambda.arn)
          # The loader/enrichment response carries no adapter identity, so
          # inject it alongside the response and its covered window keys.
          Payload = "{% $merge([$states.input, {'adapter_type': '${var.namespace}'}]) %}"
        }
        Output = "{% $states.result.Payload %}"
        Next   = "Success"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          },
          {
            # Iceberg optimistic-concurrency commit conflicts; re-stamping is a no-op.
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 5
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
    }
  ]...)

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Adapter pipeline (trigger, loader, publish event)"
    StartAt       = local.id_mode_enabled ? "Which mode?" : "Run trigger"
    States = merge(
      local.base_states,
      local.id_mode_states,
      local.reconcile_states,
      local.enrichment_states,
      local.mark_published_states,
    )
  })
}

# IAM Role for State Machine
resource "aws_iam_role" "state_machine_role" {
  name = "${var.namespace}-adapter-state-machine-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
      }
    ]
  })
}

# Attach the policies to the role
resource "aws_iam_role_policy_attachment" "state_machine_lambda_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_lambda_policy.arn
}

resource "aws_iam_role_policy_attachment" "state_machine_logging_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_logging_policy.arn
}

resource "aws_iam_role_policy_attachment" "state_machine_eventbridge_put_policy_attachment" {
  role       = aws_iam_role.state_machine_role.name
  policy_arn = aws_iam_policy.state_machine_eventbridge_put_policy.arn
}

# State Machine
resource "aws_sfn_state_machine" "state_machine" {
  name       = "${var.namespace}-adapter"
  role_arn   = aws_iam_role.state_machine_role.arn
  definition = local.state_machine_definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.state_machine_logs.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }
}

# CloudWatch Log Group for State Machine
resource "aws_cloudwatch_log_group" "state_machine_logs" {
  name              = "/aws/stepfunctions/${var.namespace}-adapter-pipeline"
  retention_in_days = 14
}
