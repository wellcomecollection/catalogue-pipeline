data "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

module "ebsco" {
  source         = "./ebsco"
  repository_url = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
}

// TODO: add alarm on state machine failures and stats - see catalogue graph dash for example
// TODO: import the bucket, and any necessary parameters, from the ebsco_adapter infra

// TODO: update reindexer to use the new spec for reindexing EBSCO records