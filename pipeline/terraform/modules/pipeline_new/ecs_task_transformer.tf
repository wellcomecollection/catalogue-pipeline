# A full-store transform (a reindex, which carries no changeset ids) does not fit
# in the transformer Lambda's 600s. Harvests and rebuilds arrive pre-chunked into
# changesets and stay on the Lambda.
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

# ecs:runTask.sync needs more than the ecs_task module's invoke policy grants: it
# polls the task and manages an EventBridge rule to hear about completion.
data "aws_iam_policy_document" "transformer_run_task_sync" {
  statement {
    effect  = "Allow"
    actions = ["ecs:StopTask", "ecs:DescribeTasks"]
    # ECS task ARNs are only known at run time.
    resources = ["*"]
  }

  statement {
    effect    = "Allow"
    actions   = ["events:PutTargets", "events:PutRule", "events:DescribeRule"]
    resources = ["arn:aws:events:eu-west-1:${data.aws_caller_identity.current.account_id}:rule/StepFunctionsGetEventsForECSTaskRule"]
  }
}
