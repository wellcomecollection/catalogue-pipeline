output "task_role_name" {
  value = module.task_definition.task_role_name
}

output "task_execution_role_name" {
  value = module.task_definition.task_execution_role_name
}

output "queue_url" {
  value = module.input_queue.url
}

output "log_configuration" {
  value = module.log_router_container.container_log_configuration
}
