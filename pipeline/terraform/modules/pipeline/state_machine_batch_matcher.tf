# Experimental batch matcher (wellcomecollection/platform#6742). After every
# id-minter run it resolves the matcher graph from the works_identified Iceberg
# table and records which components changed, so the result can be checked
# against what the Scala matcher and merger did.

locals {
  batch_matcher_s3_tables_bucket_arn = "arn:aws:s3tables:eu-west-1:${data.aws_caller_identity.current.account_id}:bucket/${local.infra_critical.catalogue_pipeline_table_bucket_name}"

  batch_matcher_state_machine_definition = jsonencode({
    QueryLanguage = "JSONata"
    Comment       = "Resolve the matcher graph from the works_identified table and record the changed components."
    StartAt       = "Match"
    States = {
      Match = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Arguments = {
          FunctionName = module.batch_matcher_lambda.lambda_arn
          Payload      = "{% $states.input %}"
        }
        Output = "{% $states.result.Payload %}"
        Retry = [
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
        End = true
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
  # components; a full run peaks at about 3.2 GB.
  memory_size = 6144
  timeout     = 300

  environment_variables = {
    WORKS_IDENTIFIED_TABLE_NAME = "works_identified_${replace(var.pipeline_date, "-", "_")}"
    S3_TABLES_BUCKET            = local.infra_critical.catalogue_pipeline_table_bucket_name
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
  statement {
    actions = ["s3:GetObject", "s3:PutObject"]
    resources = [
      "arn:aws:s3:::wellcomecollection-catalogue-graph/graph-*/pipeline-${var.pipeline_date}/merger/*",
    ]
  }
}

module "batch_matcher_state_machine" {
  source = "../state_machine"

  name                     = "pipeline-${var.pipeline_date}_batch_matcher"
  state_machine_definition = local.batch_matcher_state_machine_definition
  invokable_lambda_arns    = [module.batch_matcher_lambda.lambda_arn]
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
