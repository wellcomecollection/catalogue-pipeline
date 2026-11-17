# Experimental batch matcher and merger (wellcomecollection/platform#6742,
# #6857). After every id-minter run it resolves the matcher graph from the
# works_identified Iceberg table, merges every component that changed and
# writes the results to indices of their own, so they can be checked against
# what the Scala matcher and merger did.
#
# Match records the changed components in groups; each group is merged in its
# own invocation, so a full reindex fits the Lambda limits. The components
# baseline is replaced last, so a failed group is merged again on the next run.

locals {
  batch_matcher_s3_tables_bucket_arn = "arn:aws:s3tables:eu-west-1:${data.aws_caller_identity.current.account_id}:bucket/${local.infra_critical.catalogue_pipeline_table_bucket_name}"
  batch_merger_bucket                = "wellcomecollection-catalogue-graph"

  # Each group reads the whole content column of the table, and the merge
  # writes to the shared cluster.
  batch_merger_max_concurrency = 5

  batch_matcher_lambda_retry = [
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

  batch_matcher_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Resolve the matcher graph from the works_identified table, merge the changed components and replace the components baseline."
    StartAt       = "Match"
    States = {
      Match = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.batch_matcher_lambda.lambda_arn
          Payload      = "{% $states.input %}"
        }
        Assign = {
          match = "{% $states.result.Payload %}"
        }
        Output = "{% $states.result.Payload %}"
        Retry  = local.batch_matcher_lambda_retry
        Next   = "Merge"
      }
      Merge = {
        Type           = "Map"
        Items          = "{% $states.input.merge_groups %}"
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
                FunctionName = module.batch_merger_lambda.lambda_arn
                Payload      = "{% $states.input %}"
              }
              Output = "{% $states.result.Payload %}"
              # Lambda.Unknown covers timeouts and out-of-memory; a group's
              # writes are versioned by the snapshot time, so a re-run is safe.
              Retry = concat(local.batch_matcher_lambda_retry, [
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
        Next = "CommitComponents"
      }
      CommitComponents = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:s3:copyObject"
        Arguments = {
          Bucket     = local.batch_merger_bucket
          CopySource = "{% $substringAfter($match.job_components_s3_uri, 's3://') %}"
          Key        = "{% $substringAfter($match.components_s3_uri, 's3://${local.batch_merger_bucket}/') %}"
        }
        Next = "DeleteJobComponents"
      }
      # The job's copy is the whole graph; its changed components are kept.
      DeleteJobComponents = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:s3:deleteObject"
        Arguments = {
          Bucket = local.batch_merger_bucket
          Key    = "{% $substringAfter($match.job_components_s3_uri, 's3://${local.batch_merger_bucket}/') %}"
        }
        Output = "{% $match %}"
        End    = true
      }
    }
  })
}

module "batch_matcher_lambda" {
  source = "../pipeline_lambda"

  service_name = "batch-matcher"
  description  = "Resolves the matcher graph from the works_identified Iceberg table and records the changed components."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["merger.steps.merger.lambda_handler"]
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

module "batch_merger_lambda" {
  source = "../pipeline_lambda"

  service_name = "batch-merger"
  description  = "Merges one group of the components the batch matcher found changed, and writes the merged works and images to the batch merger's indices."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["merger.steps.merge.lambda_handler"]
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

resource "aws_iam_role_policy" "batch_merger_s3_tables_read" {
  role   = module.batch_merger_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_matcher_s3_tables_read.json
}

resource "aws_iam_role_policy" "batch_merger_read" {
  role   = module.batch_merger_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_merger_read.json
}

data "aws_iam_policy_document" "batch_merger_read" {
  # The changed components of the run.
  statement {
    actions = ["s3:GetObject"]
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

resource "aws_iam_role_policy" "batch_matcher_s3_tables_read" {
  role   = module.batch_matcher_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_matcher_s3_tables_read.json
}

data "aws_iam_policy_document" "batch_matcher_s3_tables_read" {
  statement {
    actions = [
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      local.batch_matcher_s3_tables_bucket_arn,
      "${local.batch_matcher_s3_tables_bucket_arn}/*",
    ]
  }

  statement {
    actions = [
      "s3tables:GetTableMetadataLocation",
      "s3tables:GetTable",
      "s3tables:GetTableData",
    ]
    resources = ["${local.batch_matcher_s3_tables_bucket_arn}/table/*"]
  }
}

# The changed components of each run, and the baseline every run reads and replaces.
resource "aws_iam_role_policy" "batch_matcher_s3_read_write" {
  role   = module.batch_matcher_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.batch_matcher_s3_read_write.json
}

data "aws_iam_policy_document" "batch_matcher_s3_read_write" {
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

module "batch_matcher_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_batch_matcher"
  state_machine_definition = local.batch_matcher_state_machine_definition
  invokable_lambda_arns = [
    module.batch_matcher_lambda.lambda_arn,
    module.batch_merger_lambda.lambda_arn,
  ]
  policies_to_attach = {
    "components-copy" = data.aws_iam_policy_document.batch_matcher_components_copy.json
  }
}

# The last states copy the run's components over the baseline and delete the job's copy.
data "aws_iam_policy_document" "batch_matcher_components_copy" {
  statement {
    actions = ["s3:GetObject", "s3:GetObjectTagging", "s3:PutObject", "s3:PutObjectTagging", "s3:DeleteObject"]
    resources = [
      "arn:aws:s3:::${local.batch_merger_bucket}/graph-*/pipeline-${var.pipeline_date}/merger/*",
    ]
  }
}

module "batch_matcher_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.batch_matcher_state_machine.state_machine_arn
  alarm_name_prefix = "batch-matcher"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}

# Runs once per successful id-minter execution, so every run sees a snapshot
# with all of that execution's partitions committed.
module "batch_matcher_trigger" {
  source = "../state_machine_trigger"

  name              = "batch-matcher-${var.pipeline_date}"
  event_bus_name    = "default"
  state_machine_arn = module.batch_matcher_state_machine.state_machine_arn

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
