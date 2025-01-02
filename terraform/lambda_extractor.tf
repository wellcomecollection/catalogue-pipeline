module "extractor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-extractor"
  description = "Extracts source concepts and turns them into Cypher queries."
  runtime     = "python3.13"

  filename         = "../build.zip"
  source_code_hash = filesha256("../build.zip")

  handler     = "extractor.lambda_handler"

  // This Lambda does not need a lot of memory, but it downloads and processes large datasets (with up to 10 million
  // items) and therefore needs the additional compute and networking capacity which comes with increased memory.
  memory_size = 4096
  timeout     = 15*60 // 15 minutes

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_indexer_lambda_security_group.id]
  }

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
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

data "aws_iam_policy_document" "stream_to_s3" {
  statement {
    actions = [
      "s3:PutObject",
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.neptune_bulk_upload_bucket.arn}/*"
    ]
  }
}

resource "aws_iam_role_policy" "stream_to_sns_policy" {
  role   = module.extractor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.stream_to_sns.json
}

resource "aws_iam_role_policy" "stream_to_s3_policy" {
  role   = module.extractor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.stream_to_s3.json
}
