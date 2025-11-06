output "task_role_arn" {
  value = module.task_definition.task_role_arn
}

output "task_execution_role_arn" {
  value = module.task_definition.task_execution_role_arn
}

output "task_role_name" {
  value = module.task_definition.task_role_name
}

output "task_execution_role_name" {
  value = module.task_definition.task_execution_role_name
}

output "task_definition_arn" {
  value = module.task_definition.arn
}

output "invoke_policy_document" {
  value = data.aws_iam_policy_document.invoke_policy_document.json
}