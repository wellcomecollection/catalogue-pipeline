module "bulk_load_poller_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-bulk-load-poller"
  description = "Polls the status of a Neptune bulk load job."
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "bulk_load_poller.lambda_handler"
  memory_size = 128
  timeout     = 120 // 120 seconds

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_indexer_lambda_security_group.id]
  }

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_read_secrets_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_neptune_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_load_poll.json
}

resource "aws_iam_role_policy" "bulk_load_poller_s3_write" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_bulk_load_write.json
}

resource "aws_iam_role_policy" "bulk_load_poller_s3_red" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_bulk_load_read.json
}

