module "extractor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-extractor"
  description = "Extracts source concepts and turns them into Cypher queries."
  runtime     = "python3.10"

  filename = "../build.zip"
  source_code_hash = filesha256("../build.zip")

  handler     = "extractor.lambda_handler"
  memory_size = 128
  timeout     = 60 // 1 minute

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
}

data "aws_iam_policy_document" "publish_to_queries_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      module.catalogue_graph_queries_topic.arn
    ]
  }
}

resource "aws_iam_role_policy" "reindex_jobs_policy" {
  role   = module.extractor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.publish_to_queries_topic.json
}
