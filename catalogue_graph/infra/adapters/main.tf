data "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

module "ebsco" {
  source              = "./modules/adapter"
  namespace           = "ebsco"
  s3_bucket_name      = "wellcomecollection-platform-ebsco-adapter"
  schedule_expression = "cron(0 2 * * ? *)" # Daily at 2 AM UTC
  repository_url      = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name      = aws_cloudwatch_event_bus.event_bus.name
}

module "axiell" {
  source              = "./modules/adapter"
  namespace           = "axiell"
  s3_bucket_name      = "wellcomecollection-platform-axiell-adapter"
  schedule_expression = "rate(15 minutes)"
  repository_url      = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name      = aws_cloudwatch_event_bus.event_bus.name
}

module "folio" {
  source              = "./modules/adapter"
  namespace           = "folio"
  s3_bucket_name      = "wellcomecollection-platform-folio-adapter"
  schedule_expression = "rate(15 minutes)"
  repository_url      = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name      = aws_cloudwatch_event_bus.event_bus.name
}

# Event bus to enable communication with the current pipeline
# This is a shared bus intended to be used by all new adapters,
# but there's currently no other users.
resource "aws_cloudwatch_event_bus" "event_bus" {
  name = "catalogue-pipeline-adapter-event-bus"
}
