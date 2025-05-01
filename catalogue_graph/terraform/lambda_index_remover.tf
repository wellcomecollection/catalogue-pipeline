module "index_remover_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-index-remover"
  description = "Removes concepts which no longer exist in the catalogue graph from the Elasticsearch index."
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "index_remover.lambda_handler"
  memory_size = 1024
  timeout     = 60 // 1 minute

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.ec_privatelink_security_group_id
    ]
  }

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
}

resource "aws_iam_role_policy" "index_remover_lambda_read_secrets_policy" {
  role   = module.index_remover_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "index_remover_lambda_read_pipeline_secrets_policy" {
  role   = module.index_remover_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read.json
}

# Allow the Lambda to write the 'last_index_remover_run_date.txt' file
resource "aws_iam_role_policy" "index_remover_lambda_s3_write_policy" {
  role   = module.index_remover_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "index_remover_lambda_s3_read_policy" {
  role   = module.index_remover_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

# Read files outputted by the graph_remover Lambda
resource "aws_iam_role_policy" "index_remover_lambda_s3_policy" {
  role   = module.index_remover_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.index_remover_s3_policy.json
}

data "aws_iam_policy_document" "index_remover_s3_policy" {
  statement {
    actions = [
      "s3:HeadObject",
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover/*"
    ]
  }
}
