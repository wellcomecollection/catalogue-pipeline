# Scheduled image-inference state machine:
#   ConstructEvent (build the time window from the schedule)
#     -> FindWork (Lambda: ids of images modified in the window, partitioned)
#       -> InferImages (Map: one EC2 inference task per partition, runTask.waitForTaskToken)
#
# This replaces the always-on SQS-driven image_inferrer service. It is additive:
# the old service stays until cutover (see service_image_inferrer.tf). Ship the
# schedule disabled (var.enable_image_inferrer_schedule) until validated.

locals {
  # Generous timeout so EC2 capacity-provider warm-up does not trip the task token.
  inference_task_token_timeout_seconds = 3 * 60 * 60 # 3 hours

  inference_lambda_retry = [
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

  # Retry transient ECS infrastructure errors only. Application failures (e.g. a
  # poisoned-doc error) are intentionally NOT retried so they surface promptly.
  inference_ecs_retry = [
    # Capacity/placement contention. When the Map fans out runTask calls
    # concurrently, ECS can momentarily fail placement with
    # `ECS.AmazonECSException: Insufficient CPU available` if two tasks race for
    # the same instance, or while the EC2 capacity provider is still scaling up.
    # This is transient and clears once a slot frees / the reservation registers,
    # so retry it generously (long enough to outlast an in-flight task) rather
    # than letting one placement race abort the whole run.
    {
      ErrorEquals     = ["ECS.AmazonECSException"]
      IntervalSeconds = 15
      MaxAttempts     = 12
      BackoffRate     = 2.0
      MaxDelaySeconds = 60
      JitterStrategy  = "FULL"
    },
    {
      ErrorEquals = [
        "Ecs.ServerException",
        "Ecs.ThrottlingException",
        "Ecs.TaskFailedToStartException",
        "Ecs.CannotPullContainerErrorException",
        "Ecs.ContainerRuntimeTimeoutErrorException",
      ]
      IntervalSeconds = 5
      MaxAttempts     = 3
      BackoffRate     = 2.0
      JitterStrategy  = "FULL"
    }
  ]

  image_inferrer_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Find images modified within the window and augment them with inferred data."
    StartAt       = "ConstructEvent"
    States = {
      ConstructEvent = {
        Type = "Pass"
        Output = {
          "pipeline_date" : var.pipeline_date,
          "index_dates" : {
            "initial" : local.image_inferrer_initial_index_date,
            "augmented" : local.image_inferrer_augmented_index_date
          },
          # Window end is 5 minutes before the scheduled time (indexing lag);
          # the find-work step defaults the window start to end - 15 minutes.
          "window" : {
            "end_time" : "{% $fromMillis($toMillis($states.input.scheduled_time) - 300000) %}"
          }
        }
        Next = "FindWork"
      }

      FindWork = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.inference_find_work_lambda.lambda_arn
          Payload      = "{% $states.input %}"
        }
        Output = "{% $states.result.Payload %}"
        Retry  = local.inference_lambda_retry
        Next   = "InferImages"
      }

      InferImages = {
        Type  = "Map"
        Items = "{% $states.input.partitions %}"
        # Matches the inferrer ASG max_instances (local.inference_max_concurrency)
        # so the Map never fans out more tasks than the capacity provider can place.
        MaxConcurrency = local.inference_max_concurrency
        ItemProcessor = {
          ProcessorConfig = { Mode = "INLINE" }
          StartAt         = "RunInferenceTask"
          States = {
            RunInferenceTask = {
              Type           = "Task"
              Resource       = "arn:aws:states:::ecs:runTask.waitForTaskToken"
              TimeoutSeconds = local.inference_task_token_timeout_seconds
              Retry          = local.inference_ecs_retry
              Arguments = {
                Cluster        = aws_ecs_cluster.cluster.arn
                TaskDefinition = module.inference_manager_ecs_task.task_definition_arn
                CapacityProviderStrategy = [
                  {
                    CapacityProvider = module.inference_capacity_provider.name
                    Weight           = 1
                  }
                ]
                NetworkConfiguration = {
                  AwsvpcConfiguration = {
                    AssignPublicIp = "DISABLED"
                    Subnets        = local.network_config.subnets
                    SecurityGroups = [
                      local.network_config.ec_privatelink_security_group_id,
                      aws_security_group.egress.id,
                    ]
                  }
                }
                Overrides = {
                  ContainerOverrides = [
                    {
                      Name = local.inference_manager_container_name
                      Command = [
                        "/app/src/inferrer/steps/inference_manager.py",
                        "--event", "{% $string($states.input) %}",
                        "--task-token", "{% $states.context.Task.Token %}",
                      ]
                    }
                  ]
                }
              }
              # A partition that still fails after task-level retries (an
              # exhausted capacity retry, a permanently-missing image, or an ES
              # error) is recorded and tolerated rather than aborting the whole
              # Map. Its images are left un-augmented and picked up by a later
              # window (writes are idempotent external_gte). Without this, one bad
              # partition fails-fast the entire run.
              Catch = [
                {
                  ErrorEquals = ["States.ALL"]
                  Next        = "RecordPartitionFailure"
                }
              ]
              End = true
            }
            RecordPartitionFailure = {
              Type = "Pass"
              Output = {
                "partition_failed" : true
              }
              End = true
            }
          }
        }
        End = true
      }
    }
  })
}

module "image_inferrer_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_image_inferrer"
  state_machine_definition = local.image_inferrer_state_machine_definition

  invokable_lambda_arns = [module.inference_find_work_lambda.lambda_arn]

  policies_to_attach = {
    "inference_manager_ecs_task_invoke_policy" = module.inference_manager_ecs_task.invoke_policy_document
  }
}

module "image_inferrer_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.image_inferrer_state_machine.state_machine_arn
  alarm_name_prefix = "image-inferrer"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

# --- EventBridge schedule -------------------------------------------------- #

resource "aws_scheduler_schedule" "image_inferrer_schedule" {
  name                = "image-inferrer-schedule-${var.pipeline_date}"
  schedule_expression = "cron(0,15,30,45 * * * ? *)"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.image_inferrer_state_machine.state_machine_arn
    role_arn = aws_iam_role.run_image_inferrer_role.arn

    input = <<JSON
    {
      "scheduled_time": "<aws.scheduler.scheduled-time>"
    }
    JSON
  }

  state = var.enable_image_inferrer_schedule ? "ENABLED" : "DISABLED"
}

resource "aws_iam_role" "run_image_inferrer_role" {
  name = "run-image-inferrer-role-${var.pipeline_date}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "scheduler.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "run_image_inferrer_policy" {
  role = aws_iam_role.run_image_inferrer_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = module.image_inferrer_state_machine.state_machine_arn
    }]
  })
}
