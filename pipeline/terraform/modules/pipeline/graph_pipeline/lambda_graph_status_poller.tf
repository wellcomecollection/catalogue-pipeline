module "graph_status_poller_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "graph-status-poller-${var.pipeline_date}"
  description  = "Checks the status of the serverless Neptune cluster."
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["graph_status_poller.lambda_handler"]
  }

  memory_size = 256
  timeout     = 60 // 60 seconds

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_pipeline_security_group.id]
  }
}


data "aws_iam_policy_document" "neptune_status_poller" {
  statement {
    actions = [
      "rds:DescribeDBClusters"
    ]

    resources = [
      data.terraform_remote_state.catalogue_graph.outputs.neptune_cluster_arn
    ]
  }
}

resource "aws_iam_role_policy" "graph_status_poller_lambda_neptune_policy" {
  role   = module.graph_status_poller_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_status_poller.json
}
