module "elasticsearch_pit_opener_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-es-pit-opener"
  description  = "Opens a point in time against the denormalised index"
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["pit_opener.lambda_handler"]
  }

  memory_size = 128
  timeout     = 15

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.ec_privatelink_security_group_id
    ]
  }
}

resource "aws_iam_role_policy" "pit_opener_lambda_read_pipeline_secrets_policy" {
  role   = module.elasticsearch_pit_opener_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read_denormalised_read_only.json
}
