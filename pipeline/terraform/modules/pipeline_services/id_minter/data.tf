data "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

data "aws_region" "current" {}
data "aws_caller_identity" "current" {}