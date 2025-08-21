module "bulk_loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-bulk-loader"
  description = "Bulk loads entities from an S3 bucket into the Neptune database."
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "bulk_loader.lambda_handler"
  memory_size = 128
  timeout     = 30 // 30 seconds

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_indexer_lambda_security_group.id]
  }

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
    }
  }

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
}

# S3 bucket for storing files to be bulk loaded into the Neptune cluster
resource "aws_s3_bucket" "neptune_bulk_upload_bucket" {
  bucket = "wellcomecollection-neptune-graph-loader"
}

resource "aws_iam_role_policy" "bulk_loader_lambda_read_secrets_policy" {
  role   = module.bulk_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

data "aws_iam_policy_document" "neptune_load_poll" {
  statement {
    actions = [
      "neptune-db:StartLoaderJob",
      "neptune-db:GetLoaderJobStatus"
    ]

    resources = [
      "*"
    ]
  }
}

resource "aws_iam_role_policy" "bulk_loader_lambda_neptune_policy" {
  role   = module.bulk_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_load_poll.json
}
