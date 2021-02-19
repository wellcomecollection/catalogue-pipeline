module "deletion_check_initiator_lambda" {
  source = "./modules/scheduled_lambda"

  name = "deletion_check_initiator"
  description = "Sends requests for a deletion check to the reindexer"

  s3_bucket = local.infra_bucket
  s3_key = "lambdas/calm_adapter/calm_deletion_check_initiator.zip"
  schedule_interval = local.deletion_check_interval

  env_vars = {
    REINDEXER_TOPIC_ARN = local.reindex_jobs_topic_arn
    SOURCE_TABLE_NAME = module.vhs.table_name
  }
}

data "aws_iam_policy_document" "publish_to_reindex_jobs_topic" {
  statement {
    actions = [
      "sns:Publish",
    ]

    resources = [
      local.reindex_jobs_topic_arn
    ]
  }
}

resource "aws_iam_role_policy" "reindex_jobs_policy" {
  role   = module.deletion_check_initiator_lambda.role_name
  policy = data.aws_iam_policy_document.publish_to_reindex_jobs_topic.json
}
