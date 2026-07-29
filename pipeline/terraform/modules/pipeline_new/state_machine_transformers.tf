data "aws_cloudwatch_event_bus" "adapter_event_bus" {
  name = "catalogue-pipeline-adapter-event-bus"
}

module "transformer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${local.namespace}-transformer"
  description  = "Lambda function to transform EBSCO/Axiell/Folio data"
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  # CI deploys via `update-function-code --publish` and nothing consumes a
  # versioned ARN, so Terraform publishing only caused a perpetual version diff.
  publish = false

  image_config = {
    command = ["adapters.steps.transformer.lambda_handler"]
  }

  # 10240 (up from 4096) to accommodate the FOLIO item-enrichment join. The bib
  # store is sorted by id, so a changeset read cannot prune and materialises most
  # of the table; on the FOLIO enrichment path this tips a single-record transform
  # over 4096. Interim mitigation until the changeset read is made prunable (#3444).
  memory_size = 10240
  timeout     = 600

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  environment = {
    variables = {
      PIPELINE_DATE = var.pipeline_date
      INDEX_DATE    = var.index_dates.source
      S3_PREFIX     = "prod"
    }
  }
}

# "all_adapter" -> see transformer_types below

# Attach read-only Iceberg access policies to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_iceberg_read" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.all_adapter_s3tables_read.json
}

# Attach S3 read policies to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_s3_read" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.all_adapter_buckets_read.json
}

# Attach S3 write policies to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_s3_write" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.all_adapter_buckets_write.json
}

# Allow transformer to read pipeline storage secrets
resource "aws_iam_role_policy" "transformer_lambda_pipeline_storage_secret_read" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.all_transformer_pipeline_storage_secrets.json
}

# State Machine Definition
locals {
  transformer_state_machine_definition = jsonencode({
    StartAt = "Run transformer"
    States = {
      "Run transformer" = {
        Type      = "Task"
        Resource  = module.transformer_lambda.lambda.arn
        InputPath = "$.detail"
        Next      = "Success"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      "Success" = {
        Type = "Succeed"
      }
    }
  })

  transformer_types = {
    ebsco = {
      adapter_source       = "ebsco.adapter"
      adapter_detail_type  = "ebsco.adapter.completed"
      reindex_target_value = "ebsco"
    }
    axiell = {
      adapter_source       = "axiell.adapter"
      adapter_detail_type  = "axiell.adapter.completed"
      reindex_target_value = "axiell"
    }
    #    folio = {
    #      adapter_source       = "folio.adapter"
    #      adapter_detail_type  = "folio.adapter.completed"
    #      reindex_target_value = "folio"
    #    }
  }
}


module "transformer_state_machine" {
  source = "../state_machine"

  name                     = "transformer-${var.pipeline_date}"
  state_machine_definition = local.transformer_state_machine_definition
  invokable_lambda_arns = [
    module.transformer_lambda.lambda.arn,
  ]

  policies_to_attach = {
    "read_ebsco_adapter_bucket"  = data.aws_iam_policy_document.adapter_bucket_read["ebsco"].json
    "read_axiell_adapter_bucket" = data.aws_iam_policy_document.adapter_bucket_read["axiell"].json
    "read_folio_adapter_bucket"  = data.aws_iam_policy_document.adapter_bucket_read["folio"].json
  }
}

module "transformer_state_machine_alarms" {
  source = "../state_machine_alarms"

  state_machine_arn = module.transformer_state_machine.state_machine_arn
  alarm_name_prefix = "transformer-state-machine"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
  }
}


resource "aws_iam_role_policy" "transformer_lambda_cloudwatch_write" {
  role   = module.transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.transformer_lambda_cloudwatch_write.json
}

data "aws_iam_policy_document" "transformer_lambda_cloudwatch_write" {
  statement {
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]
  }
}

resource "aws_cloudwatch_metric_alarm" "transformer_failures" {
  for_each = toset(keys(local.transformer_types))

  alarm_name          = "${each.key}-transformer-failures-${var.pipeline_date}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "failure_count"
  namespace           = "catalogue_graph_pipeline"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "${each.key} adapter transformer Lambda reported transformation failures"

  dimensions = {
    pipeline_date    = var.pipeline_date
    pipeline_step    = "adapter_transformer"
    transformer_type = each.key
  }

  alarm_actions = [local.monitoring_infra["chatbot_topic_arn"]]
}

# Trigger State Machine on adapter completed events
module "adapter_transformer_trigger" {
  for_each = local.transformer_types
  source   = "../state_machine_trigger"

  name              = "${each.key}-transformer-${var.pipeline_date}"
  event_bus_name    = data.aws_cloudwatch_event_bus.adapter_event_bus.name
  state_machine_arn = module.transformer_state_machine.state_machine_arn

  enabled = var.enable_adapter_transformer_trigger

  event_pattern = {
    source        = [each.value.adapter_source],
    "detail-type" = [each.value.adapter_detail_type]
  }
  // Unfortunately the input template needs to be a full JSON object,
  // so we must wrap the detail in another object and then unwrap in
  // the state machine (it's not possible to just pass the detail directly).
  input_paths = {
    detail = "$.detail"
  }
  input_template = "{\"detail\": <detail>}"
}

# Trigger State Machine on weco.pipeline.reindex events
module "reindex_transformer_trigger" {
  for_each = local.transformer_types
  source   = "../state_machine_trigger"

  name              = "${each.key}-reindex-${var.pipeline_date}"
  event_bus_name    = data.aws_cloudwatch_event_bus.adapter_event_bus.name
  state_machine_arn = module.transformer_state_machine.state_machine_arn

  enabled = var.reindexing_state.listen_to_reindexer

  // Expect events like:
  // {
  //   "source": "weco.pipeline.reindex",
  //   "detail-type": "weco.pipeline.reindex.requested",
  //   "detail": {
  //     "reindex_targets": ["<adapter>"],
  //     "job_id": "some-unique-id"
  //   }
  // }
  event_pattern = {
    source        = ["weco.pipeline.reindex"],
    "detail-type" = ["weco.pipeline.reindex.requested"],
    detail = {
      reindex_targets = [each.value.reindex_target_value]
    }
  }

  input_paths = {
    job_id = "$.detail.job_id"
  }
  input_template = "{\"detail\": {\"job_id\": <job_id>, \"transformer_type\": \"${each.key}\"}}"
}
