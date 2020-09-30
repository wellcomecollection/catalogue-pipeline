module "scheduler_topic" {
  source = "../../modules/topic"

  name       = "snapshot_schedule-${var.deployment_service_env}"
  role_names = [module.snapshot_scheduler.role_name]
}
