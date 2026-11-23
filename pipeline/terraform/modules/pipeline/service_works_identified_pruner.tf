# Prunes superseded rows from the works_identified table
# (wellcomecollection/platform#6858). Described in catalogue_graph/src/id_minter/README.md.

locals {
  works_identified_pruner_s3_tables_bucket_arn = "arn:aws:s3tables:eu-west-1:${data.aws_caller_identity.current.account_id}:bucket/${local.infra_critical.catalogue_pipeline_table_bucket_name}"

  # Matches PRUNER_SUPERSEDED_THRESHOLD, the share of superseded rows a run prunes at.
  works_identified_pruner_threshold = 0.05

  works_identified_pruner_alarm_tags = {
    "backplane:component"     = "catalogue-graph"
    "github.com/project-slug" = "wellcomecollection/catalogue-pipeline"
    severity                  = "medium"
  }
}

module "works_identified_pruner_lambda" {
  source = "../pipeline_lambda"

  service_name = "works-identified-pruner"
  description  = "Rewrites the works_identified Iceberg table without the rows a later write superseded."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["id_minter.steps.pruner.lambda_handler"]
  }

  # Holds the id, version and write time of every row, about 1.4 GB at 6 million
  # rows, plus one row group of documents while it copies a file.
  memory_size = 4096
  timeout     = 900

  environment_variables = {
    PIPELINE_DATE               = var.pipeline_date
    WORKS_IDENTIFIED_TABLE_NAME = "works_identified_${replace(var.pipeline_date, "-", "_")}"
    S3_TABLES_BUCKET            = local.infra_critical.catalogue_pipeline_table_bucket_name
  }
}

resource "aws_iam_role_policy" "works_identified_pruner_access" {
  role   = module.works_identified_pruner_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.works_identified_pruner_access.json
}

data "aws_iam_policy_document" "works_identified_pruner_access" {
  statement {
    actions = [
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      local.works_identified_pruner_s3_tables_bucket_arn,
      "${local.works_identified_pruner_s3_tables_bucket_arn}/*",
    ]
  }

  # Reads every data file, writes the pruned copies and commits the swap.
  statement {
    actions = [
      "s3tables:GetTableMetadataLocation",
      "s3tables:GetTable",
      "s3tables:GetTableData",
      "s3tables:PutTableData",
      "s3tables:UpdateTableMetadataLocation",
    ]
    resources = ["${local.works_identified_pruner_s3_tables_bucket_arn}/table/*"]
  }

  statement {
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]
  }
}

# Hourly, so a reindex is pruned while it runs. A run below the threshold only
# reads the id, version and write time of every row.
resource "aws_scheduler_schedule" "works_identified_pruner" {
  name = "works-identified-pruner-${var.pipeline_date}"

  schedule_expression = "cron(40 * * * ? *)" # Clear of the id minter's quarter-hourly commits

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = module.works_identified_pruner_lambda.lambda_arn
    role_arn = aws_iam_role.run_works_identified_pruner.arn

    # A run is not repeated: the next hour's run starts afresh.
    retry_policy {
      maximum_retry_attempts = 0
    }
  }

  state = var.enable_experimental_merger ? "ENABLED" : "DISABLED"
}

resource "aws_iam_role" "run_works_identified_pruner" {
  name = "run-works-identified-pruner-${var.pipeline_date}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "scheduler.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy" "run_works_identified_pruner" {
  role = aws_iam_role.run_works_identified_pruner.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = module.works_identified_pruner_lambda.lambda_arn
      }
    ]
  })
}

# The schedule invokes the Lambda asynchronously, and Lambda would otherwise
# run a failed prune twice more.
resource "aws_lambda_function_event_invoke_config" "works_identified_pruner" {
  function_name          = module.works_identified_pruner_lambda.lambda_arn
  maximum_retry_attempts = 0
}

resource "aws_cloudwatch_metric_alarm" "works_identified_pruner_errors" {
  count = var.enable_experimental_merger ? 1 : 0

  alarm_name          = "works-identified-pruner-errors-${var.pipeline_date}"
  alarm_description   = "[component=catalogue-graph severity=medium] The works_identified pruner Lambda failed or timed out"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 3600
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = "catalogue-${var.pipeline_date}-works-identified-pruner"
  }

  alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  tags          = local.works_identified_pruner_alarm_tags
}

# A run that prunes reports no superseded rows, and one below the threshold
# reports fewer than the threshold. Three runs at or above it mean the table is
# growing and the pruner is not keeping up.
resource "aws_cloudwatch_metric_alarm" "works_identified_superseded_rows" {
  count = var.enable_experimental_merger ? 1 : 0

  alarm_name          = "works-identified-superseded-rows-${var.pipeline_date}"
  alarm_description   = "[component=catalogue-graph severity=medium] Superseded rows in the works_identified table have stayed above the pruner's threshold for three runs"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 3
  threshold           = local.works_identified_pruner_threshold
  treat_missing_data  = "missing"

  metric_query {
    id          = "superseded_share"
    expression  = "superseded / rows"
    label       = "Share of rows that are superseded"
    return_data = true
  }

  metric_query {
    id = "superseded"

    metric {
      metric_name = "superseded_row_count"
      namespace   = "catalogue_graph_pipeline"
      period      = 3600
      stat        = "Maximum"

      dimensions = {
        pipeline_date = var.pipeline_date
        pipeline_step = "works_identified_pruner"
      }
    }
  }

  metric_query {
    id = "rows"

    metric {
      metric_name = "row_count"
      namespace   = "catalogue_graph_pipeline"
      period      = 3600
      stat        = "Maximum"

      dimensions = {
        pipeline_date = var.pipeline_date
        pipeline_step = "works_identified_pruner"
      }
    }
  }

  alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  tags          = local.works_identified_pruner_alarm_tags
}

# The id minter only logs and counts an append it had to give up on, and those
# rows are then missing from the table.
resource "aws_cloudwatch_metric_alarm" "works_identified_append_failures" {
  count = var.enable_experimental_merger ? 1 : 0

  alarm_name          = "works-identified-append-failures-${var.pipeline_date}"
  alarm_description   = "[component=catalogue-graph severity=medium] The id minter failed to append works to the works_identified table"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "iceberg_failure_count"
  namespace           = "catalogue_graph_pipeline"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  dimensions = {
    pipeline_date = var.pipeline_date
    pipeline_step = "id_minter"
  }

  alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  tags          = local.works_identified_pruner_alarm_tags
}
