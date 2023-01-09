output "task_role_name" {
  value = module.scaling_service.task_role_name
}

output "queue_url" {
  value = module.input_queue.url
}
