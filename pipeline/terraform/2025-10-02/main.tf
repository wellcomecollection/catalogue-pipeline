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
    augmented = "2026-06-15"
    works     = "2026-03-03"
    concepts  = "2026-03-03"
    images    = "2026-04-29"
  }

  # Image-inferrer. The scheduled Python inferrer is the sole inferrer: it reads images-initial-2026-06-15
  # and writes images-augmented-2026-06-15, which the graph read-path also reads.
  # graph_index_dates.augmented = "2026-06-15" is the single source for both. image_inferrer_initial_index_date
  # is overridden because it otherwise falls back to pipeline_date.
  image_inferrer_initial_index_date = "2026-06-15"

  # Base AMI for ECS instances
  ami_id = "resolve:ssm:arn:aws:ssm:eu-west-1:760097843905:parameter/imagebuilder/weco-al2023-ecs-optimised-x86_64/latest"

  pipeline_date = local.pipeline_date
  es_cluster_date = local.es_cluster_date
  release_label = local.pipeline_date

  elastic_outputs = module.elastic

  // test services use a count guard so that we don't also create test services in further pipelines
  enabled_services = [
    "matcher_test",
    "merger_test",
    "id_minter_test",
  ]

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
