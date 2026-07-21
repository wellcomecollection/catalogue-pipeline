module "ingestor_deletions_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-ingestor-deletions"
  description  = "Removes concepts which no longer exist in the catalogue graph from the Elasticsearch index."

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["ingestor.steps.ingestor_deletions.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 60 // 1 minute

  environment_variables = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
  }

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_read_secrets_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
}

# Allow the Lambda to write the 'report.ingestor_deletions.json' file
resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_write_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_read_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

# Read files outputted by the graph_remover Lambda
resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.ingestor_deletions_s3_policy.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_cloudwatch_write_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}
