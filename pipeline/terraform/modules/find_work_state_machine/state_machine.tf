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

  process_partitions = {
    Type           = "Map"
    Items          = "{% $states.input.partitions %}"
    MaxConcurrency = var.max_concurrency
    ItemProcessor = {
      ProcessorConfig = { Mode = "INLINE" }
      StartAt         = var.worker_state_name
      States = {
        (var.worker_state_name) = merge(var.worker_state, {
          # Record a failed partition (ref + truncated error) and keep the Map running.
          Catch = [
            {
              ErrorEquals = ["States.ALL"]
              Output      = "{% {'partition_failed': true, 's3_uri': $states.input.s3_uri, 'error': $substring($string($states.errorOutput), 0, 1000)} %}"
              Next        = "RecordPartitionFailure"
            }
          ]
          End = true
        })
        RecordPartitionFailure = {
          Type = "Pass"
          End  = true
        }
      }
    }
    # Aggregate counts plus the failure records only: keeping every success
    # output would let the Map output grow with partition count towards the
    # 256 KB state limit.
    Output = "{% {'partition_count': $count($states.result), 'failed_partition_count': $count($states.result[partition_failed = true]), 'failed_partitions': [$states.result[partition_failed = true]]} %}"
    Next   = "CheckPartitionFailures"
  }

  # Fail only after every partition has run, so a replay covers just the failed
  # partitions. When tolerated, the Choice is constant-false so the shape stays identical.
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
      Cause = "{% $string($states.input.failed_partition_count) & ' of ' & $string($states.input.partition_count) & ' partitions failed; failed_partitions in the Map output has each ref and error' %}"
    }
    Succeeded = {
      Type = "Succeed"
    }
  }

  # The raw invocation payload (scheduled_time, or replay input) goes straight
  # to the find-work Lambda, which owns all input normalisation and guards.
  state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = var.comment
    StartAt       = "FindWork"
    States = merge(
      {
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
  alarm_name_prefix = coalesce(var.alarm_name_prefix, local.slug)
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [var.alarm_topic_arn]
  }
}
