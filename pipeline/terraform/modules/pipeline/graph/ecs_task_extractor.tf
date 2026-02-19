module "extractor_ecs_task" {
  source = "../../ecs_task"

  task_name = "graph-extractor-${var.pipeline_date}"

  image = "${data.aws_ecr_repository.unified_pipeline_task.repository_url}:env.${var.pipeline_date}"

  environment = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
    LOG_LEVEL                 = "INFO" # Set log level to INFO for testing, WARNING later
  }

  cpu    = 4096
  memory = 16384
}

resource "aws_iam_role_policy" "ecs_stream_to_s3_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_write.json
}

resource "aws_iam_role_policy" "ecs_read_s3_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_read.json
}

resource "aws_iam_role_policy" "graph_extractor_task_cloudwatch_write_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}

resource "aws_iam_role_policy" "graph_extractor_ecs_read_pipeline_secrets_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read_denormalised_read_only.json
}
