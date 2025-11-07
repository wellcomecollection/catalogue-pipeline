module "elasticsearch_pit_opener_lambda" {
  source = "../../pipeline_lambda"

  service_name = "graph-es-pit-opener"
  description  = "Opens a point in time against the denormalised index"

  pipeline_date = var.pipeline_date

  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["pit_opener.lambda_handler"]
  }

  memory_size = 256
  timeout     = 60

  vpc_config = local.lambda_vpc_config
}

resource "aws_iam_role_policy" "pit_opener_lambda_read_pipeline_secrets_policy" {
  role   = module.elasticsearch_pit_opener_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read_denormalised_read_only.json
}
