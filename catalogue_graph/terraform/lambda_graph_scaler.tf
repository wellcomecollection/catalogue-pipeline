module "graph_scaler_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-scaler"
  description = "Sets the minimum and maximum capacity of the serverless Neptune cluster."
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "graph_scaler.lambda_handler"
  memory_size = 128
  timeout     = 900 // 15 minutes

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_indexer_lambda_security_group.id]
  }
}
