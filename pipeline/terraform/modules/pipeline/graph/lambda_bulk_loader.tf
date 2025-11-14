module "bulk_loader_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-bulk-loader"
  description  = "Bulk loads entities from an S3 bucket into the Neptune database."

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["bulk_loader.lambda_handler"]
  }

  memory_size = 256
  timeout     = 60

  environment_variables = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
    LOG_LEVEL                 = "INFO" # Set log level to INFO for testing, WARNING later
  }

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "bulk_loader_lambda_read_secrets_policy" {
  role   = module.bulk_loader_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
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
  role   = module.bulk_loader_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_load_poll.json
}

resource "aws_iam_role_policy" "bulk_loader_lambda_cloudwatch_write_policy" {
  role   = module.bulk_loader_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}
