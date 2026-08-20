# Scheduled id-minter on the shared find_work_state_machine module: FindWork
# partitions the works indexed in the window, and each partition mints in its
# own Lambda invocation, so execution time no longer depends on window density
# (the platform#6445 losses were three dense windows blowing the 900s timeout).
# tolerate_partition_failures is false because nothing re-covers a missed
# indexed_at range: a lost partition must fail the execution, not skip silently.

locals {
  # Concurrency measured during the platform#6445 recovery: 6-way was the sweet
  # spot against the RDS ACU ceiling; 8 gave no gain and more timeouts.
  id_minter_max_concurrency = 6

  id_minter_worker_retry = [
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
    },
    # Lambda.Unknown covers function timeouts/OOM/crashes, not application
    # errors; minting is idempotent so a wholesale partition re-run is safe.
    {
      ErrorEquals     = ["Lambda.Unknown"]
      IntervalSeconds = 5
      MaxAttempts     = 2
      BackoffRate     = 2.0
    }
  ]
}

module "id_minter" {
  source = "../find_work_state_machine"

  name          = "id_minter"
  pipeline_date = var.pipeline_date
  comment       = "Find works indexed within the window and mint canonical ids for them."

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  find_work_lambda = {
    service_name = "id-minter-find-work"
    description  = "Finds works needing id minting within a time window and partitions them for the id-minter state machine."
    command      = ["id_minter.steps.find_work.lambda_handler"]
    # Sized for reindex-density windows, which materialise every matching id;
    # 2048 OOMed on ~15-min windows during the round 2 bulk load.
    memory_size = 8192
    timeout     = 600
    # Index name and ES secrets are env-derived, matching the minter itself.
    environment_variables = {
      PIPELINE_DATE               = var.pipeline_date
      GRAPH_DATE                  = var.graph_date
      ES_SOURCE_INDEX_DATE_SUFFIX = var.index_dates.source
    }
  }

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      local.network_config.ec_privatelink_security_group_id,
      aws_security_group.egress.id,
    ]
  }

  find_work_secret_read_policy_json = data.aws_iam_policy_document.id_minter_find_work_secret_read.json

  partition_s3_arns = [
    "arn:aws:s3:::wellcomecollection-catalogue-graph/graph-*/*/id_minter/*"
  ]

  max_concurrency             = local.id_minter_max_concurrency
  tolerate_partition_failures = false

  worker_state_name = "MintPartition"
  worker_state = {
    Type     = "Task"
    Resource = "arn:aws:states:::lambda:invoke"
    Arguments = {
      FunctionName = module.id_minter_lambda.id_minter_lambda_arn
      Payload      = "{% $states.input %}"
    }
    Output = "{% $states.result.Payload %}"
    Retry  = local.id_minter_worker_retry
  }
  worker_lambda_arns = [module.id_minter_lambda.id_minter_lambda_arn]

  schedule = {
    cron    = "cron(5,20,35,50 * * * ? *)"
    enabled = var.enable_id_minter_schedule
  }

  alarm_topic_arn = local.monitoring_infra["chatbot_topic_arn"]
}

data "aws_iam_policy_document" "id_minter_find_work_secret_read" {
  statement {
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "${local.secrets_manager_prefix}:elasticsearch/pipeline_storage_${var.pipeline_date}/*",
    ]
  }
}

# The minting Lambda resolves its partition ref (work ids written by find_work)
# from S3.
data "aws_iam_policy_document" "id_minter_partition_read" {
  statement {
    effect  = "Allow"
    actions = ["s3:GetObject"]
    resources = [
      "arn:aws:s3:::wellcomecollection-catalogue-graph/graph-*/*/id_minter/*"
    ]
  }
}

resource "aws_iam_role_policy" "id_minter_partition_read" {
  role   = module.id_minter_lambda.id_minter_lambda_role_name
  policy = data.aws_iam_policy_document.id_minter_partition_read.json
}

# Keep pre-module resources updating in place; removable once every stack
# using this module has applied.

moved {
  from = module.id_minter_state_machine
  to   = module.id_minter.module.state_machine
}

moved {
  from = module.id_minter_state_machine_alarms
  to   = module.id_minter.module.state_machine_alarms
}

moved {
  from = aws_scheduler_schedule.id_minter_schedule
  to   = module.id_minter.aws_scheduler_schedule.schedule
}

moved {
  from = aws_iam_role.run_id_minter_role
  to   = module.id_minter.aws_iam_role.run_state_machine
}

moved {
  from = aws_iam_role_policy.run_id_minter_policy
  to   = module.id_minter.aws_iam_role_policy.run_state_machine
}
