module "graph_status_poller_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-status-poller"
  description  = "Checks the status of the serverless Neptune cluster."
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["graph_status_poller.lambda_handler"]
  }

  memory_size = 128
  timeout     = 30 // 30 seconds

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_indexer_lambda_security_group.id]
  }
}


data "aws_iam_policy_document" "neptune_status_poller" {
  statement {
    actions = [
      "rds:DescribeDBClusters"
    ]

    resources = [
      aws_neptune_cluster.catalogue_graph_cluster.arn
    ]
  }
}

resource "aws_iam_role_policy" "graph_status_poller_lambda_neptune_policy" {
  role   = module.graph_status_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_status_poller.json
}
