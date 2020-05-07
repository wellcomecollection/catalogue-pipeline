output "task_role_name" {
  value = module.task_definition.task_role_name
}

output "task_role_arn" {
  value = module.task_definition.task_role_arn
}

output "task_execution_role_name" {
  value = module.task_definition.task_execution_role_name
}

output "scale_up_arn" {
  value = module.autoscaling.scale_up_arn
}

output "scale_down_arn" {
  value = module.autoscaling.scale_down_arn
}
