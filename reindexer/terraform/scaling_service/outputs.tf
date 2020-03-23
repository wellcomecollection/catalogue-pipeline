output "service_name" {
  value = module.service.name
}

output "task_definition_arn" {
  value = module.task.arn
}

output "task_role_name" {
  value = module.task.task_role_name
}

output "scale_up_arn" {
  value = module.appautoscaling.scale_up_arn
}

output "scale_down_arn" {
  value = module.appautoscaling.scale_down_arn
}
