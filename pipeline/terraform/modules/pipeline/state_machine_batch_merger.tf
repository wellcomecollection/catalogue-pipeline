# Experimental batch merger (wellcomecollection/platform#6742,
# #6857). After every id-minter run it resolves the matcher graph from the
# works_identified Iceberg table, merges every component that changed and
# writes the results to indices of their own, so they can be checked against
# what the Scala matcher and merger did.
#
# Match records the changed components in groups; each group is merged in its
# own invocation, so a full reindex fits the Lambda limits. Commit replaces the
# components baseline last, leaving out components that failed, so the next run
# merges them again without holding up the rest.

locals {
  batch_merger_s3_tables_bucket_arn = "arn:aws:s3tables:eu-west-1:${data.aws_caller_identity.current.account_id}:bucket/${local.infra_critical.catalogue_pipeline_table_bucket_name}"
  batch_merger_bucket               = "wellcomecollection-catalogue-graph"

  # Each group reads the whole content column of the table, and the merge
  # writes to the shared cluster.
  batch_merger_max_concurrency = 5

  batch_merger_lambda_retry = [
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

  batch_merger_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Resolve the matcher graph from the works_identified table, merge the changed components and replace the components baseline."
    StartAt       = "Match"
    States = {
      Match = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.batch_merger_match_lambda.lambda_arn
          Payload      = "{% $states.input %}"
        }
        Assign = {
          match = "{% $states.result.Payload %}"
        }
        Output = "{% $states.result.Payload %}"
        Retry  = local.batch_merger_lambda_retry
        Next   = "Merge"
      }
      Merge = {
        Type  = "Map"
        Items = "{% [0..$match.group_count - 1] %}"
        # The run's input, with the job and snapshot the Match step settled on.
        # Trimmed, as Step Functions reads a string as JSONata only between {% and %}.
        ItemSelector = trimspace(<<-EOT
          {% $merge([
            $states.context.Execution.Input,
            {
              'job_id': $match.job_id,
              'iceberg_snapshot_id': $match.iceberg_snapshot_id,
              'group': $states.context.Map.Item.Value
            }
          ]) %}
        EOT
        )
        MaxConcurrency = local.batch_merger_max_concurrency
        ItemProcessor = {
          ProcessorConfig = {
            Mode = "INLINE"
          }
          StartAt = "MergeGroup"
          States = {
            MergeGroup = {
              Type     = "Task"
              Resource = "arn:aws:states:::lambda:invoke"
              Arguments = {
                FunctionName = module.batch_merger_group_lambda.lambda_arn
                Payload      = "{% $states.input %}"
              }
              Output = "{% $states.result.Payload %}"
              # Lambda.Unknown covers timeouts and out-of-memory; a group's
              # writes are versioned by the snapshot time, so a re-run is safe.
              Retry = concat(local.batch_merger_lambda_retry, [
                {
                  ErrorEquals     = ["Lambda.Unknown"]
                  IntervalSeconds = 5
                  MaxAttempts     = 2
                  BackoffRate     = 2.0
                }
              ])
              End = true
            }
          }
        }
        Next = "Commit"
      }
      Commit = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.batch_merger_commit_lambda.lambda_arn
          Payload      = "{% $merge([$states.context.Execution.Input, {'job_id': $match.job_id, 'group_count': $match.group_count}]) %}"
        }
        Output = "{% $merge([$match, $states.result.Payload]) %}"
        Retry  = local.batch_merger_lambda_retry
        Next   = "CheckFailures"
      }
      # Failed components were left out of the baseline, so the next run retries them;
      # failing the run raises the alarm while they keep failing.
      CheckFailures = {
        Type = "Choice"
        Choices = [
          {
            Condition = "{% $states.input.failed_component_count > 0 %}"
            Next      = "ComponentsFailed"
          }
        ]
        Default = "Done"
      }
      ComponentsFailed = {
        Type  = "Fail"
        Error = "ComponentsFailed"
        Cause = "{% $string($states.input.failed_component_count) & ' components failed to merge; see the failed_components files in the job folder' %}"
      }
      Done = {
        Type = "Succeed"
      }
    }
  })
}

module "batch_merger_match_lambda" {
  source = "../pipeline_lambda"

  service_name = "batch-merger-match"
  description  = "Resolves the matcher graph from the works_identified Iceberg table and splits the changed components into groups to merge."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["merger.steps.match.lambda_handler"]
  }

  # Holds the graph columns of every row in the table while it resolves
  # components; a full run peaks at about 2 GB.
  memory_size = 4096
  timeout     = 300

  environment_variables = {
    WORKS_IDENTIFIED_TABLE_NAME = "works_identified_${replace(var.pipeline_date, "-", "_")}"
    S3_TABLES_BUCKET            = local.infra_critical.catalogue_pipeline_table_bucket_name
  }
}

module "batch_merger_group_lambda" {
  source = "../pipeline_lambda"

  service_name = "batch-merger-group"
  description  = "Merges one group of the changed components, and writes the merged works and images to the batch merger's indices."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["merger.steps.merge_group.lambda_handler"]
  }

  # A group's documents are fetched in one read of the table's content column,
  # about 3 GB at its peak, and held while they are merged.
  memory_size = 8192
  timeout     = 900

  vpc_config = local.lambda_vpc_config

  environment_variables = {
    WORKS_IDENTIFIED_TABLE_NAME = "works_identified_${replace(var.pipeline_date, "-", "_")}"
    S3_TABLES_BUCKET            = local.infra_critical.catalogue_pipeline_table_bucket_name
  }
}

resource "aws_iam_role_policy" "batch_merger_group_s3_tables_read" {
  role   = module.batch_merger_group_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_merger_s3_tables_read.json
}

resource "aws_iam_role_policy" "batch_merger_group_access" {
  role   = module.batch_merger_group_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_merger_group_access.json
}

data "aws_iam_policy_document" "batch_merger_group_access" {
  # Reads the run's changed components and writes the ones that failed. smart_open
  # writes by multipart upload and aborts it when a write fails.
  statement {
    actions = ["s3:GetObject", "s3:PutObject", "s3:AbortMultipartUpload"]
    resources = [
      "arn:aws:s3:::${local.batch_merger_bucket}/graph-*/pipeline-${var.pipeline_date}/merger/*",
    ]
  }

  # The connection details and API key the Lambda reads at start-up.
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "${local.secrets_manager_prefix}:elasticsearch/pipeline_storage_${var.pipeline_date}/*",
    ]
  }
}

module "batch_merger_commit_lambda" {
  source = "../pipeline_lambda"

  service_name = "batch-merger-commit"
  description  = "Replaces the batch merger's components baseline with the run's components, leaving out those that failed to merge."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["merger.steps.commit.lambda_handler"]
  }

  # Reads and writes the components of the whole graph, about 3 million rows.
  memory_size = 2048
  timeout     = 300
}

resource "aws_iam_role_policy" "batch_merger_commit_s3" {
  role   = module.batch_merger_commit_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_merger_commit_s3.json
}

data "aws_iam_policy_document" "batch_merger_commit_s3" {
  # Reads the run's components and failures, writes the baseline and deletes the run's copy.
  statement {
    actions = ["s3:GetObject", "s3:PutObject", "s3:AbortMultipartUpload", "s3:DeleteObject"]
    resources = [
      "arn:aws:s3:::${local.batch_merger_bucket}/graph-*/pipeline-${var.pipeline_date}/merger/*",
    ]
  }
}

resource "aws_iam_role_policy" "batch_merger_match_s3_tables_read" {
  role   = module.batch_merger_match_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_merger_s3_tables_read.json
}

data "aws_iam_policy_document" "batch_merger_s3_tables_read" {
  statement {
    actions = [
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      local.batch_merger_s3_tables_bucket_arn,
      "${local.batch_merger_s3_tables_bucket_arn}/*",
    ]
  }

  statement {
    actions = [
      "s3tables:GetTableMetadataLocation",
      "s3tables:GetTable",
      "s3tables:GetTableData",
    ]
    resources = ["${local.batch_merger_s3_tables_bucket_arn}/table/*"]
  }
}

# The run's files, and the baseline every run reads.
resource "aws_iam_role_policy" "batch_merger_match_s3_read_write" {
  role   = module.batch_merger_match_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_merger_match_s3_read_write.json
}

data "aws_iam_policy_document" "batch_merger_match_s3_read_write" {
  # smart_open writes by multipart upload and aborts it when a write fails.
  statement {
    actions = ["s3:GetObject", "s3:PutObject", "s3:AbortMultipartUpload"]
    resources = [
      "arn:aws:s3:::wellcomecollection-catalogue-graph/graph-*/pipeline-${var.pipeline_date}/merger/*",
    ]
  }

  # Without list permission S3 answers a read of a missing key with 403, which
  # the step could not tell from a refused read.
  statement {
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::wellcomecollection-catalogue-graph"]
  }
}

module "batch_merger_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_batch_merger"
  state_machine_definition = local.batch_merger_state_machine_definition
  invokable_lambda_arns = [
    module.batch_merger_match_lambda.lambda_arn,
    module.batch_merger_group_lambda.lambda_arn,
    module.batch_merger_commit_lambda.lambda_arn,
  ]
}

module "batch_merger_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.batch_merger_state_machine.state_machine_arn
  alarm_name_prefix = "batch-merger"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

# Runs once per successful id-minter execution, so every run sees a snapshot
# with all of that execution's partitions committed.
module "batch_merger_trigger" {
  source = "../state_machine_trigger"

  name              = "batch-merger-${var.pipeline_date}"
  event_bus_name    = "default"
  state_machine_arn = module.batch_merger_state_machine.state_machine_arn

  enabled = var.enable_experimental_merger

  event_pattern = {
    source        = ["aws.states"]
    "detail-type" = ["Step Functions Execution Status Change"]
    detail = {
      status          = ["SUCCEEDED"]
      stateMachineArn = [module.id_minter.state_machine_arn]
    }
  }

  input_template = "{\"pipeline_date\": \"${var.pipeline_date}\", \"graph_date\": \"${var.graph_date}\"}"
}
