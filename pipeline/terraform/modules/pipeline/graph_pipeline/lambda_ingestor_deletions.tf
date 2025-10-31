module "ingestor_deletions_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${local.namespace}-ingestor-deletions-${var.pipeline_date}"
  description  = "Removes concepts which no longer exist in the catalogue graph from the Elasticsearch index."
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_deletions.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 60 // 1 minute

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.graph_pipeline_security_group.id,
      local.ec_privatelink_security_group_id
    ]
  }
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_read_secrets_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
}

# Allow the Lambda to write the 'report.ingestor_deletions.json' file
resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_write_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_read_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

# Read files outputted by the graph_remover Lambda
resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_deletions_s3_policy.json
}
