# FOLIO item-enrichment ECS task.
# Gated by var.enable_item_enrichment so only the FOLIO adapter provisions it.
# Mirrors ecs_loader.tf: same image, IAM shape, and Step Functions callback.
# See https://github.com/wellcomecollection/catalogue-pipeline/pull/3438.

module "enrichment_ecs_task" {
  count  = var.enable_item_enrichment ? 1 : 0
  source = "../../../../../pipeline/terraform/modules/ecs_task"

  task_name = "${var.namespace}-adapter-enrichment"
  image = "${var.task_repository_url}:prod"

  # Steady state is light, but size for backfill windows (matches the loader).
  cpu    = 2048
  memory = 8192

  environment = {
    S3_BUCKET = data.aws_s3_bucket.adapter.id
    S3_PREFIX = "prod"
    # Inventory URL, OKAPI credentials and tenant are read from SSM at runtime
    # (/catalogue_pipeline/folio/inventory_*).
  }
}

# Read + write the FOLIO Iceberg tables (bib changeset in, items rows out).
resource "aws_iam_role_policy" "enrichment_task_iceberg_write" {
  count  = var.enable_item_enrichment ? 1 : 0
  role   = module.enrichment_ecs_task[0].task_role_name
  policy = data.aws_iam_policy_document.iceberg_write.json
}

resource "aws_iam_role_policy" "enrichment_task_s3_read" {
  count  = var.enable_item_enrichment ? 1 : 0
  role   = module.enrichment_ecs_task[0].task_role_name
  policy = data.aws_iam_policy_document.s3_read.json
}

resource "aws_iam_role_policy" "enrichment_task_s3_write" {
  count  = var.enable_item_enrichment ? 1 : 0
  role   = module.enrichment_ecs_task[0].task_role_name
  policy = data.aws_iam_policy_document.s3_write.json
}

# Read the inventory token/url from SSM (scoped to /catalogue_pipeline/<namespace>*).
resource "aws_iam_role_policy" "enrichment_task_ssm_read" {
  count  = var.enable_item_enrichment ? 1 : 0
  role   = module.enrichment_ecs_task[0].task_role_name
  policy = data.aws_iam_policy_document.ssm_read.json
}

resource "aws_iam_role_policy" "enrichment_task_cloudwatch_put_metric" {
  count  = var.enable_item_enrichment ? 1 : 0
  role   = module.enrichment_ecs_task[0].task_role_name
  policy = data.aws_iam_policy_document.cloudwatch_put_metric_data.json
}

# Allow the enrichment task to report back to Step Functions (waitForTaskToken).
resource "aws_iam_role_policy" "enrichment_task_sfn_callback" {
  count = var.enable_item_enrichment ? 1 : 0
  role  = module.enrichment_ecs_task[0].task_role_name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "states:SendTaskSuccess",
          "states:SendTaskFailure",
        ]
        Resource = [
          aws_sfn_state_machine.state_machine.arn,
        ]
      }
    ]
  })
}

# Allow the state machine to run the enrichment task (runTask + passRole).
resource "aws_iam_role_policy" "state_machine_ecs_run_enrichment_task" {
  count  = var.enable_item_enrichment ? 1 : 0
  role   = aws_iam_role.state_machine_role.name
  policy = module.enrichment_ecs_task[0].invoke_policy_document
}
