module "pipeline" {
  source = "../modules/pipeline"

  reindexing_state = {
    listen_to_reindexer      = true
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_matcher_db      = false
  }

  # Default values for a new pipeline
  # graph_index_dates = {
  #   merged   = local.pipeline_date
  #   works    = local.pipeline_date
  #   concepts = local.pipeline_date
  # }

  graph_index_dates = {
    merged    = "2025-10-02"
    augmented = "2026-04-29"
    works     = "2026-03-03"
    concepts  = "2026-03-03"
    images    = "2026-04-29"
  }

  # Base AMI for ECS instances
  ami_id = "resolve:ssm:arn:aws:ssm:eu-west-1:760097843905:parameter/imagebuilder/weco-al2023-ecs-optimised-x86_64/latest"

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  elastic_outputs = module.elastic

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
