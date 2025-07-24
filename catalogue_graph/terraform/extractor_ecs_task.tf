resource "aws_ecs_cluster" "cluster" {
  name = local.namespace
}

module "extractor_ecs_task" {
  source = "./modules/ecs_task"

  task_name = "${local.namespace}_extractor"

  image = "${aws_ecr_repository.catalogue_graph_extractor.repository_url}:dev"

  environment = {
    S3_BULK_LOAD_BUCKET_NAME    = aws_s3_bucket.neptune_bulk_upload_bucket.bucket
    GRAPH_QUERIES_SNS_TOPIC_ARN = module.catalogue_graph_queries_topic.arn
  }

  cpu    = 4096
  memory = 16384
}

resource "aws_iam_role_policy" "ecs_stream_to_sns_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.stream_to_sns.json
}

resource "aws_iam_role_policy" "ecs_stream_to_s3_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_write.json
}

resource "aws_iam_role_policy" "ecs_read_s3_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_read.json
}

# openCypher queries will be streamed to this SNS topic (when SNS is chosen as the streaming destination)
module "catalogue_graph_queries_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "catalogue_graph_queries"
}

data "aws_iam_policy_document" "stream_to_sns" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.catalogue_graph_queries_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "graph_extractor_ecs_read_pipeline_secrets_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.extractor_allow_pipeline_storage_secret_read.json
}
