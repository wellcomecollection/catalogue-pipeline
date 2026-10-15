# Probes the third-party APIs the adapters depend on every 5 minutes, recording
# latency and availability as EMF metrics in the third_party_probes namespace.
# Code: catalogue_graph/src/probes.

locals {
  probe_vendors = {
    sierra      = ["auth", "read"]
    axiell_oai  = ["identify", "read"]
    folio_oai   = ["identify", "read"]
    folio_okapi = ["auth", "read"]
    ebsco_ftp   = ["auth", "read"]
  }
  probe_credentials_prefix = "/catalogue_pipeline/third_party_probes"
}

# Values start as copies of the adapter credentials, filled in by hand after
# apply, so the probes can move to their own credentials later.
resource "aws_ssm_parameter" "probe_credentials" {
  for_each = local.probe_vendors

  name        = "${local.probe_credentials_prefix}/${each.key}"
  description = "JSON credentials for the ${each.key} third-party probe"
  type        = "SecureString"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [value]
  }
}

module "probe_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-third-party-probe"
  description  = "Measures latency and availability of the adapters' third-party APIs"
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  # CI deploys via `update-function-code --publish`; nothing consumes a versioned ARN.
  publish = false

  image_config = {
    command = ["probes.handler.lambda_handler"]
  }

  memory_size = 256
  timeout     = 120

  # Same egress path as the adapter ECS tasks, so any vendor IP allowlisting applies.
  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.adapter_egress.id]
  }

  environment = {
    variables = {
      CREDENTIALS_PREFIX        = local.probe_credentials_prefix
      FOLIO_PROBE_INSTANCE_HRID = "in00008304342"
    }
  }
}

data "aws_iam_policy_document" "probe_ssm_read" {
  statement {
    actions   = ["ssm:GetParameter"]
    resources = [for p in aws_ssm_parameter.probe_credentials : p.arn]
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = ["arn:aws:kms:eu-west-1:760097843905:key/*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.eu-west-1.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "probe_ssm_read" {
  role   = module.probe_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.probe_ssm_read.json
}

resource "aws_iam_role" "probe_scheduler" {
  name = "catalogue-third-party-probe-scheduler"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "probe_scheduler_invoke" {
  role = aws_iam_role.probe_scheduler.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = module.probe_lambda.lambda.arn
    }]
  })
}

resource "aws_scheduler_schedule" "probe" {
  for_each = local.probe_vendors

  name = "third_party_probe_${each.key}"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "rate(5 minutes)"

  target {
    arn      = module.probe_lambda.lambda.arn
    role_arn = aws_iam_role.probe_scheduler.arn
    input    = jsonencode({ vendor = each.key })

    # A retried probe would double-count; the next run is 5 minutes away.
    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

resource "aws_cloudwatch_dashboard" "probes" {
  dashboard_name = "third-party-probes"

  dashboard_body = jsonencode({
    widgets = flatten([
      for i, vendor in keys(local.probe_vendors) : [
        {
          type   = "metric"
          x      = 0
          y      = i * 6
          width  = 16
          height = 6
          properties = {
            title  = "${vendor}: successful step duration (ms)"
            region = "eu-west-1"
            view   = "timeSeries"
            period = 300
            metrics = flatten([
              for j, step in local.probe_vendors[vendor] : [
                for stat in ["p50", "p90", "p99"] : [
                  "third_party_probes", "Duration",
                  "Vendor", vendor, "Step", step, "Outcome", "ok",
                  { stat = stat, label = "${step} ${stat}" }
                ]
              ]
            ])
          }
        },
        {
          type   = "metric"
          x      = 16
          y      = i * 6
          width  = 8
          height = 6
          properties = {
            title  = "${vendor}: success rate (%)"
            region = "eu-west-1"
            view   = "timeSeries"
            period = 300
            yAxis  = { left = { min = 0, max = 100 } }
            metrics = [
              [{
                # Every run attempts the first step, and a run only reaches "read" if it passed.
                expression = "100 * SUM(SEARCH('{third_party_probes,Vendor,Step,Outcome} Vendor=\"${vendor}\" Step=\"read\" Outcome=\"ok\"', 'SampleCount', 300)) / SUM(SEARCH('{third_party_probes,Vendor,Step,Outcome} Vendor=\"${vendor}\" Step=\"${local.probe_vendors[vendor][0]}\"', 'SampleCount', 300))"
                label      = "runs where read succeeded"
              }]
            ]
          }
        },
      ]
    ])
  })
}
