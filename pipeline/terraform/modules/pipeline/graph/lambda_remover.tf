module "graph_remover_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-remover"
  description  = "Takes snapshots of items bulk loaded into the catalogue graph and handles the removal of nodes/edges."

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["graph_remover.lambda_handler"]
  }

  memory_size = 4096
  timeout     = 900 // 15 minutes

  environment_variables = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
  }

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "graph_remover_lambda_neptune_read_policy" {
  role   = module.graph_remover_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_read.json
}

resource "aws_iam_role_policy" "graph_remover_lambda_neptune_delete_policy" {
  role   = module.graph_remover_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_delete.json
}

resource "aws_iam_role_policy" "graph_remover_lambda_read_secrets_policy" {
  role   = module.graph_remover_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

# Read bulk load files outputted by the extractor
resource "aws_iam_role_policy" "graph_remover_lambda_s3_bulk_load_policy" {
  role   = module.graph_remover_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.s3_bulk_load_read.json
}

# Read and write ID snapshots and files storing added/deleted nodes/edges
resource "aws_iam_role_policy" "graph_remover_lambda_s3_policy" {
  role   = module.graph_remover_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.graph_remover_s3_policy.json
}

resource "aws_iam_role_policy" "graph_remover_lambda_cloudwatch_write_policy" {
  role   = module.graph_remover_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}

data "aws_iam_policy_document" "graph_remover_s3_policy" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:PutObject",
      "s3:HeadObject",
      "s3:GetObject"
    ]

    resources = [
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover/*"
    ]
  }
}

