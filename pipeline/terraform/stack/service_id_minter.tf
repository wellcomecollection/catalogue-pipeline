module "id_minter_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_id_minter_output"
  role_names = [module.id_minter.task_role_name]
}

module "id_minter" {
  source = "../modules/fargate_service"

  name            = "id_minter"
  container_image = local.id_minter_image

  deployment_service_name = "id-minter"

  security_group_ids = [
    var.rds_ids_access_security_group_id,
  ]

  topic_arns = [
    module.transformer_calm_output_topic.arn,
    module.transformer_mets_output_topic.arn,
    module.transformer_miro_output_topic.arn,
    module.transformer_sierra_output_topic.arn,
    module.transformer_tei_output_topic.arn,
  ]

  queue_visibility_timeout_seconds = 120

  env_vars = {
    topic_arn                     = module.id_minter_output_topic.arn
    max_connections               = local.id_minter_task_max_connections
    es_source_index               = local.es_works_source_index
    es_identified_index           = local.es_works_identified_index
    ingest_batch_size             = 100
    ingest_flush_interval_seconds = 30
  }

  secret_env_vars = merge({
    cluster_url          = "rds/identifiers-delta-cluster/endpoint"
    cluster_url_readonly = "rds/identifiers-delta-cluster/reader_endpoint"
    db_port              = "rds/identifiers-delta-cluster/port"
    db_username          = "catalogue/id_minter/rds_user"
    db_password          = "catalogue/id_minter/rds_password"
  }, local.pipeline_storage_es_service_secrets["id_minter"])

  // The total number of connections to RDS across all tasks from all ID minter
  // services must not exceed the maximum supported by the RDS instance.
  min_capacity = var.min_capacity
  max_capacity = min(
    floor(
      local.id_minter_rds_max_connections / local.id_minter_task_max_connections
    ),
    local.max_capacity
  )

  cpu    = 1024
  memory = 2048

  # Below this line is boilerplate that should be the same across
  # all Fargate services.
  egress_security_group_id             = aws_security_group.service_egress.id
  elastic_cloud_vpce_security_group_id = var.ec_privatelink_security_group_id

  cluster_name = aws_ecs_cluster.cluster.name
  cluster_arn  = aws_ecs_cluster.cluster.id

  scale_down_adjustment = local.scale_down_adjustment
  scale_up_adjustment   = local.scale_up_adjustment

  dlq_alarm_topic_arn = var.dlq_alarm_arn

  subnets = var.subnets

  namespace = local.namespace

  deployment_service_env = var.release_label

  shared_logging_secrets = var.shared_logging_secrets
}
