module "graph_remover_incremental_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-remover-incremental"
  description  = "Handles the incremental removal of catalogue nodes/edges from the catalogue graph."

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["graph_remover_incremental.lambda_handler"]
  }

  memory_size = 2048
  timeout     = 900 // 15 minutes

  environment_variables = {
    CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
  }

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "graph_remover_incremental_lambda_neptune_read_policy" {
  role   = module.graph_remover_incremental_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_read.json
}

resource "aws_iam_role_policy" "graph_remover_incremental_lambda_neptune_delete_policy" {
  role   = module.graph_remover_incremental_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.neptune_delete.json
}

resource "aws_iam_role_policy" "graph_remover_incremental_lambda_read_secrets_policy" {
  role   = module.graph_remover_incremental_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_catalogue_graph_secret_read.json
}

# Write files storing deleted nodes/edges
resource "aws_iam_role_policy" "graph_remover_incremental_lambda_s3_policy" {
  role   = module.graph_remover_incremental_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.graph_remover_incremental_s3_policy.json
}

data "aws_iam_policy_document" "graph_remover_incremental_s3_policy" {
  statement {
    actions = [
      "s3:ListBucket",
      "s3:PutObject",
      "s3:HeadObject"
    ]

    resources = [
      "${data.aws_s3_bucket.catalogue_graph_bucket.arn}/graph_remover_incremental/*"
    ]
  }
}

resource "aws_iam_role_policy" "graph_remover_incremental_ecs_read_pipeline_secrets_policy" {
  role   = module.graph_remover_incremental_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read_denormalised_read_only.json
}

resource "aws_iam_role_policy" "graph_remover_incremental_lambda_cloudwatch_write_policy" {
  role   = module.graph_remover_incremental_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}
