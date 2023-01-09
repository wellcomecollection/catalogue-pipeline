output "task_role_name" {
  value = module.scaling_service.task_role_name
}

output "task_role_arn" {
  value = module.scaling_service.task_role_arn
}

output "task_execution_role_name" {
  value = module.scaling_service.task_execution_role_name
}

output "scale_up_arn" {
  value = module.scaling_service.scale_up_arn
}

output "scale_down_arn" {
  value = module.scaling_service.scale_down_arn
}
