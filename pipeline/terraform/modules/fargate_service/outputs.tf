output "task_role_name" {
  value = module.worker.task_role_name
}

output "queue_url" {
  value = module.input_queue.url
}
