module "graph_scaler_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${local.namespace}-scaler-${var.pipeline_date}"
  description  = "Sets the minimum and maximum capacity of the serverless Neptune cluster."
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["graph_scaler.lambda_handler"]
  }

  memory_size = 128
  timeout     = 30 // 30 seconds

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_pipeline_security_group.id]
  }
}


data "aws_iam_policy_document" "neptune_scale" {
  statement {
    actions = [
      "rds:DescribeDBClusters",
      "rds:ModifyDBCluster"
    ]

    resources = [
      module.catalogue_graph_neptune_cluster.neptune_cluster_arn
    ]
  }
}

resource "aws_iam_role_policy" "graph_scaler_lambda_neptune_policy" {
  role   = module.graph_scaler_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_scale.json
}
