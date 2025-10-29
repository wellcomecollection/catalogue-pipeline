module "ingestor_loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${local.namespace}-ingestor-loader-${var.pipeline_date}"
  description  = "Loads catalogue concepts into S3 from Neptune"
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_loader.lambda_handler"]
  }

  memory_size = 4096
  timeout     = 900

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.graph_pipeline_security_group.id,
      local.ec_privatelink_security_group_id
    ]
  }

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_read_secrets_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_s3_read_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_s3_write_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_neptune_read_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_read.json
}
