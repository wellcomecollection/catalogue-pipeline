module "worker" {
  source = "../../infrastructure/modules/worker"

  name = local.namespace

  image              = local.calm_adapter_image
  env_vars           = local.env_vars
  secret_env_vars    = local.secret_env_vars
  min_capacity       = 1
  max_capacity       = 2
  desired_task_count = 1
  cpu                = 512
  memory             = 1024

  cluster_name = local.namespace
  cluster_arn  = aws_ecs_cluster.cluster.arn
  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id
  subnets      = local.private_subnets
}
