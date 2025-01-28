resource "aws_ecs_cluster" "cluster" {
  name = local.namespace
}

module "extractor_ecs_task" {
  source = "./modules/ecs_task"

  task_name = "${local.namespace}_extractor"

  image = "${aws_ecr_repository.catalogue_graph_extractor.repository_url}:dev"

  environment = {
      S3_BULK_LOAD_BUCKET_NAME = aws_s3_bucket.neptune_bulk_upload_bucket.bucket
      GRAPH_QUERIES_SNS_TOPIC_ARN = module.catalogue_graph_queries_topic.arn
  }

  cpu    = 1024
  memory = 2048
}

resource "aws_iam_role_policy" "ecs_stream_to_sns_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.stream_to_sns.json
}

resource "aws_iam_role_policy" "ecs_stream_to_s3_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.stream_to_s3.json
}
