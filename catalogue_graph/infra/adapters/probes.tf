# Probes the third-party APIs the adapters depend on every 5 minutes, recording
# latency and availability as EMF metrics in the third_party_probes namespace.
# Code: catalogue_graph/src/probes.

locals {
  probe_vendors            = toset(["sierra", "axiell_oai", "folio_oai", "folio_okapi", "ebsco_ftp"])
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
  # Two steps at up to 10s connect plus 60s read each.
  timeout = 180

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

# The scheduler's retry_policy does not cover Lambda's own async retries.
resource "aws_lambda_function_event_invoke_config" "probe" {
  function_name          = module.probe_lambda.lambda.function_name
  maximum_retry_attempts = 0
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
