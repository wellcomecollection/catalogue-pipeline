module "bulk_load_poller_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-bulk-load-poller"
  description  = "Polls the status of a Neptune bulk load job."

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["bulk_load_poller.lambda_handler"]
  }

  memory_size = 256
  timeout     = 60

  secret_env_vars = {
    SLACK_SECRET_ID = local.slack_webhook
  }

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_read_secrets_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_neptune_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_load_poll.json
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_read_slack_secret_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_slack_secret_read.json
}

resource "aws_iam_role_policy" "bulk_load_poller_s3_write" {
  role   = module.bulk_load_poller_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_write.json
}

resource "aws_iam_role_policy" "bulk_load_poller_s3_red" {
  role   = module.bulk_load_poller_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_read.json
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_cloudwatch_write_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}

