locals {
  # When item enrichment is enabled, the loader routes through a "Run enrichment"
  # state before publishing, so both the bib and item changesets exist by the time
  # "folio.adapter.completed" fires. Otherwise the loader goes straight to publish.
  loader_next = var.enable_item_enrichment ? "Should enrich?" : "Should publish event?"

  # With published tracking, both the publish and the quiet no-publish paths end
  # in "Mark published", which stamps the covered windows so the trigger resumes
  # from the last published window. The quiet path matters: quiet windows are
  # success rows too, and skipping them would stall the published cursor.
  post_publish_next = var.enable_published_tracking ? "Mark published" : "Success"

  # Carry the trigger's harvest window past the loader so "Mark published"
  # knows the covered range; loader and enrichment responses only carry
  # job_id and changeset ids.
  window_passthrough_output = merge([
    for _ in range(var.enable_published_tracking ? 1 : 0) : {
      Output = "{% $merge([$states.result, {'window': $states.input.window}]) %}"
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
        Type     = "Task"
        Resource = "arn:aws:states:::ecs:runTask.waitForTaskToken"
        Next     = "Should publish event?"
        Retry = [
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
      }, local.window_passthrough_output)
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
    "Run loader" = merge({
      Type     = "Task"
      Resource = "arn:aws:states:::ecs:runTask.waitForTaskToken"
      Next     = local.loader_next
      Retry = [
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
    }, local.window_passthrough_output)
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
    for _ in range(var.enable_published_tracking ? 1 : 0) : {
      "Mark published" = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = one(module.mark_published_lambda[*].lambda.arn)
          # The loader/enrichment response carries no adapter identity, so
          # inject it alongside the response and the threaded window.
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
    StartAt       = "Run trigger"
    States        = merge(local.base_states, local.enrichment_states, local.mark_published_states)
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
