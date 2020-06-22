module "worker" {
  source = "../../../../infrastructure/modules/worker"

  name  = var.service_name
  image = var.container_image

  env_vars        = var.env_vars
  secret_env_vars = var.secret_env_vars

  subnets = var.subnets

  cluster_name = var.cluster_name
  cluster_arn  = var.cluster_arn

  desired_task_count = var.desired_task_count

  security_group_ids = var.security_group_ids

  cpu    = var.cpu
  memory = var.memory

  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
}
