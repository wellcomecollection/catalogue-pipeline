module "ingestor_relabelled_works_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-ingestor-relabelled-works"
  description  = "Finds the works to re-ingest because a concept they display was relabelled."

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["ingestor.steps.ingestor_relabelled_works.lambda_handler"]
  }

  # Holds the concept ids and labels of two full ingests while it compares them.
  memory_size = 2048
  timeout     = 900 // 15 minutes

  environment_variables = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
  }

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "ingestor_relabelled_works_lambda_neptune_read_policy" {
  role   = module.ingestor_relabelled_works_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_read.json
}

resource "aws_iam_role_policy" "ingestor_relabelled_works_lambda_read_secrets_policy" {
  role   = module.ingestor_relabelled_works_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

# Read the documents written by the concepts ingestor loader
resource "aws_iam_role_policy" "ingestor_relabelled_works_lambda_s3_read_policy" {
  role   = module.ingestor_relabelled_works_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

# Write the 'report.relabelled_works.json' file
resource "aws_iam_role_policy" "ingestor_relabelled_works_lambda_s3_write_policy" {
  role   = module.ingestor_relabelled_works_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_relabelled_works_lambda_cloudwatch_write_policy" {
  role   = module.ingestor_relabelled_works_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}
