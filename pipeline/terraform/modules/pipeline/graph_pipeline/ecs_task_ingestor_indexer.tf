module "ingestor_indexer_ecs_task" {
  source = "./ecs_task"

  task_name = "ingestor-indexer-${var.pipeline_date}"

  image = "${data.aws_ecr_repository.unified_pipeline_task.repository_url}:dev"

  environment = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
    INGESTOR_S3_PREFIX        = "ingestor"
  }

  cpu    = 4096
  memory = 16384
}

data "aws_iam_policy_document" "allow_ingestor_indexer_task_token" {
  statement {
    effect = "Allow"
    actions = [
      "states:SendTaskSuccess",
      "states:SendTaskFailure",
    ]
    resources = [
      module.catalogue_graph_ingestor_state_machine.state_machine_arn,
    ]
  }
}

resource "aws_iam_role_policy" "allow_ingestor_indexer_task_token_policy" {
  role   = module.ingestor_indexer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.allow_ingestor_indexer_task_token.json
}

resource "aws_iam_role_policy" "ingestor_indexer_task_read_secrets_policy" {
  role   = module.ingestor_indexer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_task_read_pipeline_secrets_policy" {
  role   = module.ingestor_indexer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_task_s3_read_policy" {
  role   = module.ingestor_indexer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_task_s3_write_policy" {
  role   = module.ingestor_indexer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_indexer_task_cloudwatch_write_policy" {
  role   = module.ingestor_indexer_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}