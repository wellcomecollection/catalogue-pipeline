module "worker" {
  source = "../../infrastructure/modules/worker"

  name = local.namespace

  image           = local.calm_adapter_image
  env_vars        = local.env_vars
  secret_env_vars = local.secret_env_vars

  min_capacity = 0
  max_capacity = 2

  cpu    = 512
  memory = 1024

  cluster_name           = aws_ecs_cluster.cluster.name
  cluster_arn            = aws_ecs_cluster.cluster.arn
  namespace_id           = aws_service_discovery_private_dns_namespace.namespace.id
  subnets                = local.private_subnets
  shared_logging_secrets = data.terraform_remote_state.shared_infra.outputs.shared_secrets_logging
}
