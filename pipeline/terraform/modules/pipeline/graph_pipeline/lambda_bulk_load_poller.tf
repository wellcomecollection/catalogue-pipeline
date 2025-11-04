module "bulk_load_poller_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "graph-bulk-load-poller-${var.pipeline_date}"
  description  = "Polls the status of a Neptune bulk load job."
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["bulk_load_poller.lambda_handler"]
  }

  memory_size = 256
  timeout     = 60 // 60 seconds

  environment = {
    variables = {
      SLACK_SECRET_ID = local.slack_webhook
    }
  }

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_pipeline_security_group.id]
  }
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_read_secrets_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_neptune_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_load_poll.json
}

resource "aws_iam_role_policy" "bulk_load_poller_lambda_read_slack_secret_policy" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_slack_secret_read.json
}

resource "aws_iam_role_policy" "bulk_load_poller_s3_write" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_bulk_load_write.json
}

resource "aws_iam_role_policy" "bulk_load_poller_s3_red" {
  role   = module.bulk_load_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.s3_bulk_load_read.json
}

