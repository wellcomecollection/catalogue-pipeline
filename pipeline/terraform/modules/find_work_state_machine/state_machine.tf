locals {
  slug = replace(var.name, "_", "-")

  lambda_retry = [
    {
      ErrorEquals = [
        "Lambda.ServiceException",
        "Lambda.AWSLambdaException",
        "Lambda.SdkClientException",
        "Lambda.TooManyRequestsException",
      ]
      IntervalSeconds = 2
      MaxAttempts     = 3
      BackoffRate     = 2.0
    }
  ]

  # Scheduled runs derive the window end from scheduled_time - 5min (indexing
  # lag); the find-work step defaults the start to end - 15min. Replays may
  # instead pass explicit ids or a window, plus a job_id and partition_size.
  # Shape guards keep malformed input (e.g. window: null) from reaching the
  # Lambda as "no window", which would scan the full index.
  construct_event_output = trimspace(<<-EOT
    {% $merge([
      ${jsonencode(merge({ pipeline_date = var.pipeline_date }, var.static_event_fields))},
      $type($states.input.ids) = 'array'
        ? {'ids': $states.input.ids}
        : {'window': $exists($states.input.window.end_time)
            ? $states.input.window
            : {'end_time': $fromMillis($toMillis($states.input.scheduled_time) - 300000)}},
      $type($states.input.job_id) = 'string' ? {'job_id': $states.input.job_id} : {},
      $type($states.input.partition_size) = 'number' ? {'partition_size': $states.input.partition_size} : {}
    ]) %}
  EOT
  )

  process_partitions = {
    Type           = "Map"
    Items          = "{% $states.input.partitions %}"
    MaxConcurrency = var.max_concurrency
    ItemProcessor = {
      ProcessorConfig = { Mode = "INLINE" }
      StartAt         = var.worker_state_name
      States = {
        (var.worker_state_name) = merge(var.worker_state, {
          # A partition that still fails after the worker's own retries is
          # recorded rather than aborting the whole Map, so every other
          # partition still runs; CheckPartitionFailures decides afterwards
          # what that means for the execution.
          Catch = [
            {
              ErrorEquals = ["States.ALL"]
              Next        = "RecordPartitionFailure"
            }
          ]
          End = true
        })
        RecordPartitionFailure = {
          Type = "Pass"
          Output = {
            "partition_failed" : true
          }
          End = true
        }
      }
    }
    Output = "{% {'partition_count': $count($states.result), 'failed_partition_count': $count($states.result[partition_failed = true]), 'results': $states.result} %}"
    Next   = "CheckPartitionFailures"
  }

  # Failing only after every partition has run means a replay needs to cover
  # just the failed partitions, not the whole window. When failures are
  # tolerated the check is a constant-false Choice, so the states stay
  # structurally identical either way.
  failure_check_states = {
    CheckPartitionFailures = {
      Type = "Choice"
      Choices = [
        {
          Condition = var.tolerate_partition_failures ? "{% false %}" : "{% $states.input.failed_partition_count > 0 %}"
          Next      = "FailPartitions"
        }
      ]
      Default = "Succeeded"
    }
    FailPartitions = {
      Type  = "Fail"
      Error = "PartitionsFailed"
      Cause = "{% $string($states.input.failed_partition_count) & ' of ' & $string($states.input.partition_count) & ' partitions failed; see the Map results for detail' %}"
    }
    Succeeded = {
      Type = "Succeed"
    }
  }

  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = var.comment
    StartAt       = "ConstructEvent"
    States = merge(
      {
        ConstructEvent = {
          Type   = "Pass"
          Output = local.construct_event_output
          Next   = "FindWork"
        }
        FindWork = {
          Type     = "Task"
          Resource = "arn:aws:states:::lambda:invoke"
          Arguments = {
            FunctionName = module.find_work_lambda.lambda_arn
            Payload      = "{% $states.input %}"
          }
          Output = "{% $states.result.Payload %}"
          Retry  = local.lambda_retry
          Next   = "ProcessPartitions"
        }
        ProcessPartitions = local.process_partitions
      },
      local.failure_check_states
    )
  })
}

module "state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_${var.name}"
  state_machine_definition = local.state_machine_definition

  invokable_lambda_arns = concat([module.find_work_lambda.lambda_arn], var.worker_lambda_arns)

  policies_to_attach = var.state_machine_policies
}

module "state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.state_machine.state_machine_arn
  alarm_name_prefix = var.alarm_name_prefix
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [var.alarm_topic_arn]
  }
}
