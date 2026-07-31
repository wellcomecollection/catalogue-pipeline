locals {
  id_minter_rds_instance = local.infra_critical.id_minter_rds[var.rds_id_minter]

  id_minter_vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.id_minter_rds_instance.ingress_security_group_id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  id_minter_env_vars = {
    LOG_LEVEL                   = "INFO"
    RDS_MAX_CONNECTIONS         = local.id_minter_task_max_connections
    ES_SOURCE_INDEX_DATE_SUFFIX = var.index_dates.source
    ES_TARGET_INDEX_DATE_SUFFIX = var.index_dates.identified
    S3_BUCKET                   = "wellcomecollection-platform-id-minter"
    S3_PREFIX                   = "prod"
  }

  # Extract the secret name from the full ARN.
  # ARN format: arn:aws:secretsmanager:region:account:secret:NAME-RANDOM
  # We strip the 6-char random suffix so the name works with GetSecretValue
  # and the pipeline_lambda IAM policy pattern (secret:NAME-*).
  rds_master_secret_name = regex(
    "arn:aws:secretsmanager:[^:]+:[^:]+:secret:(.+)-.{6}$",
    local.id_minter_rds_instance.master_user_secret_arn
  )[0]

  id_minter_secret_env_vars = merge(
    {
      RDS_PRIMARY_HOST = "rds/${local.id_minter_rds_instance.cluster_id}/endpoint"
      RDS_REPLICA_HOST = "rds/${local.id_minter_rds_instance.cluster_id}/reader_endpoint"
      RDS_PORT         = "rds/${local.id_minter_rds_instance.cluster_id}/port"
      RDS_USERNAME     = "${local.rds_master_secret_name}:username"
      RDS_PASSWORD     = "${local.rds_master_secret_name}:password"
    },
    var.elastic.pipeline_storage_es_service_secrets["id_minter"],
  )
}

module "id_minter_lambda" {
  source = "../pipeline_services/id_minter"

  pipeline_date   = var.pipeline_date
  vpc_config      = local.id_minter_vpc_config
  env_vars        = local.id_minter_env_vars
  secret_env_vars = local.id_minter_secret_env_vars
  rds_secret_name = local.rds_master_secret_name
  alarm_topic_arn = local.monitoring_infra["chatbot_topic_arn"]

  # Identifier-dense windows peak ~4.5GB; 2048 OOMs.
  lambda_memory_size = 6144
}

