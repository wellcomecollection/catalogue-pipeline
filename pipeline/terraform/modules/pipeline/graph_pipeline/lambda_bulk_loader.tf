module "bulk_loader_lambda" {
  source       = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"
  name         = "graph-bulk-loader-${var.pipeline_date}"
  description  = "Bulk loads entities from an S3 bucket into the Neptune database."
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["bulk_loader.lambda_handler"]
  }

  memory_size = 256
  timeout     = 60 // 60 seconds

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_pipeline_security_group.id]
  }

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
    }
  }
}

resource "aws_iam_role_policy" "bulk_loader_lambda_read_secrets_policy" {
  role   = module.bulk_loader_lambda.lambda_role.name
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
  role   = module.bulk_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_load_poll.json
}
