module "ingestor_loader_monitor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "${local.namespace}-ingestor-loader-monitor-${var.pipeline_date}"
  description  = "Monitors the output of ingestor_loader lambda"
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_loader_monitor.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 300

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.graph_pipeline_security_group.id,
    ]
  }

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = data.aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_loader_monitor_lambda_s3_write_policy" {
  role   = module.ingestor_loader_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_loader_monitor_lambda_s3_read_policy" {
  role   = module.ingestor_loader_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_monitor_lambda_cloudwatch_write_policy" {
  role   = module.ingestor_loader_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}
