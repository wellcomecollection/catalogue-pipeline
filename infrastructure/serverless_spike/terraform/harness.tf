# The harness runs where the merger and the ingestors run: a VPC Lambda on the
# unified pipeline image, pointed at the :dev tag with
# catalogue_graph/scripts/deploy_lambda.sh once the es_bench handler is built.
resource "aws_security_group" "harness_egress" {
  name        = "es-bench-${local.pipeline_date}_egress"
  description = "Harness egress to the public endpoint through NAT"
  vpc_id      = local.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

module "harness_lambda" {
  source = "../../../pipeline/terraform/modules/pipeline_lambda"

  service_name  = "es-bench"
  pipeline_date = local.pipeline_date
  description   = "Elasticsearch write-throughput harness for platform#6714"

  ecr_repository_name = "uk.ac.wellcome/unified_pipeline_lambda"
  # No env.<date> tag exists for the spike, and tag_override resolves to :latest.
  tag_override = "latest"

  image_config = {
    command = ["es_bench.handler.lambda_handler"]
  }

  timeout     = 900
  memory_size = 3008

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.harness_egress.id,
      aws_security_group.serverless_vpce.id,
      local.hosted_privatelink_sg_id,
    ]
  }

  environment_variables = {
    PIPELINE_DATE = local.pipeline_date
  }
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "harness_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = ["arn:aws:secretsmanager:eu-west-1:${data.aws_caller_identity.current.account_id}:secret:elasticsearch/pipeline_storage_${local.pipeline_date}/*"]
  }
}

resource "aws_iam_role_policy" "harness_secrets" {
  name   = "es-bench-${local.pipeline_date}-secrets"
  role   = module.harness_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.harness_secrets.json
}
