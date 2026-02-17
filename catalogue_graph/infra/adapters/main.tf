data "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

module "ebsco" {
  source         = "./ebsco"
  repository_url = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
}

module "axiell" {
  source         = "./axiell"
  repository_url = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
}

module "folio" {
  source         = "./folio"
  repository_url = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
}
