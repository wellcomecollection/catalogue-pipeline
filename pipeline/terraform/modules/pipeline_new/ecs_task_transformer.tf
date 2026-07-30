# Every adapter transform runs here. A reindex carries no changeset ids and has to
# stream the whole adapter store, which does not fit in the Lambda's 600s.
module "transformer_ecs_task" {
  source = "../ecs_task"

  task_name = "${local.namespace}-transformer"
  image     = "${data.aws_ecr_repository.unified_pipeline_task.repository_url}:env.${var.pipeline_date}"

  cpu    = 4096
  memory = 16384

  environment = {
    PIPELINE_DATE = var.pipeline_date
    INDEX_DATE    = var.index_dates.source
    S3_PREFIX     = "prod"
  }
}

resource "aws_iam_role_policy" "transformer_task_iceberg_read" {
  role   = module.transformer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.all_adapter_s3tables_read.json
}

resource "aws_iam_role_policy" "transformer_task_s3_read" {
  role   = module.transformer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.all_adapter_buckets_read.json
}

resource "aws_iam_role_policy" "transformer_task_s3_write" {
  role   = module.transformer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.all_adapter_buckets_write.json
}

resource "aws_iam_role_policy" "transformer_task_pipeline_storage_secret_read" {
  role   = module.transformer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.all_transformer_pipeline_storage_secrets.json
}

resource "aws_iam_role_policy" "transformer_task_cloudwatch_write" {
  role   = module.transformer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.transformer_lambda_cloudwatch_write.json
}

# The task reports completion with the Step Functions task token it is handed.
resource "aws_iam_role_policy" "transformer_task_token" {
  role   = module.transformer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.transformer_task_token.json
}

data "aws_iam_policy_document" "transformer_task_token" {
  statement {
    effect = "Allow"
    actions = [
      "states:SendTaskSuccess",
      "states:SendTaskFailure",
      "states:SendTaskHeartbeat",
    ]
    resources = ["*"]
  }
}
