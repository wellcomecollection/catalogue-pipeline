module "extractor_ecs_task" {
  source = "../../ecs_task"

  task_name = "graph-extractor-${var.pipeline_date}"

  image = "${data.aws_ecr_repository.unified_pipeline_task.repository_url}:dev"

  environment = {
    CATALOGUE_GRAPH_S3_BUCKET   = data.aws_s3_bucket.catalogue_graph_bucket.bucket
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

resource "aws_iam_role_policy" "graph_extractor_task_cloudwatch_write_policy" {
  role   = module.extractor_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}

# openCypher queries will be streamed to this SNS topic (when SNS is chosen as the streaming destination)
module "catalogue_graph_queries_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "graph-pipeline-cypher-queries-${var.pipeline_date}"
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
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read_denormalised_read_only.json
}
