# Scheduled image-inference state machine, built on the shared
# find_work_state_machine module:
#   ConstructEvent (build the time window from the schedule, or pass through replay input)
#     -> FindWork (Lambda: ids of images modified in the window, partitioned to S3)
#       -> ProcessPartitions (Map: one EC2 inference task per partition, runTask.waitForTaskToken)
#
# This is the sole image inferrer; it replaced the always-on SQS-driven image_inferrer
# Scala service, which has been retired. The schedule is gated on
# var.enable_image_inferrer_schedule.
# (The unified_pipeline_lambda ECR data source is declared in locals.tf.)

locals {
  # Generous timeout so EC2 capacity-provider warm-up does not trip the task token.
  inference_task_token_timeout_seconds = 3 * 60 * 60 # 3 hours

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
}

module "image_inferrer" {
  source = "../find_work_state_machine"

  name          = "image_inferrer"
  pipeline_date = var.pipeline_date
  comment       = "Find images modified within the window and augment them with inferred data."

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  find_work_lambda = {
    service_name = "image-inference-find-work"
    description  = "Finds images needing inference within a time window and partitions them for the image-inferrer state machine."
    command      = ["inferrer.steps.find_work.lambda_handler"]
    memory_size  = 1024
    timeout      = 300 # 5 minutes
  }

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      local.network_config.ec_privatelink_security_group_id,
      aws_security_group.egress.id,
    ]
  }

  find_work_secret_read_policy_json = data.aws_iam_policy_document.inference_manager_pipeline_storage_secret_read.json

  partition_s3_arns = [
    "arn:aws:s3:::wellcomecollection-catalogue-graph/graph-*/*/inferrer/*"
  ]

  static_event_fields = {
    graph_date = var.graph_date
    index_dates = {
      initial   = var.index_dates.initial
      augmented = var.index_dates.augmented
    }
  }

  # Matches the inferrer ASG max_instances (local.inference_max_concurrency)
  # so the Map never fans out more tasks than the capacity provider can place.
  max_concurrency = local.inference_max_concurrency

  # A failed partition's images are left un-augmented and picked up by a later
  # window (writes are idempotent external_gte), so it should not fail the run.
  tolerate_partition_failures = true

  worker_state_name = "RunInferenceTask"
  worker_state = {
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
  }

  state_machine_policies = {
    "inference_manager_ecs_task_invoke_policy" = module.inference_manager_ecs_task.invoke_policy_document
  }

  schedule = {
    cron    = "cron(0,15,30,45 * * * ? *)"
    enabled = var.enable_image_inferrer_schedule
  }

  alarm_name_prefix = "image-inferrer"
  alarm_topic_arn   = local.monitoring_infra["chatbot_topic_arn"]
}

# These resources predate the find_work_state_machine module; the moves keep
# terraform updating them in place. Removable once every stack using this
# module has applied.

moved {
  from = module.inference_find_work_lambda
  to   = module.image_inferrer.module.find_work_lambda
}

moved {
  from = aws_iam_role_policy.inference_find_work_secret_read
  to   = module.image_inferrer.aws_iam_role_policy.find_work_secret_read
}

moved {
  from = aws_iam_role_policy.inference_find_work_s3_write
  to   = module.image_inferrer.aws_iam_role_policy.find_work_s3_write
}

moved {
  from = module.image_inferrer_state_machine
  to   = module.image_inferrer.module.state_machine
}

moved {
  from = module.image_inferrer_state_machine_alarms
  to   = module.image_inferrer.module.state_machine_alarms
}

moved {
  from = aws_scheduler_schedule.image_inferrer_schedule
  to   = module.image_inferrer.aws_scheduler_schedule.schedule
}

moved {
  from = aws_iam_role.run_image_inferrer_role
  to   = module.image_inferrer.aws_iam_role.run_state_machine
}

moved {
  from = aws_iam_role_policy.run_image_inferrer_policy
  to   = module.image_inferrer.aws_iam_role_policy.run_state_machine
}
