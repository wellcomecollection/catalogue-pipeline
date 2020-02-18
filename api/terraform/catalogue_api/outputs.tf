data "aws_iam_role" "task_execution_role" {
  name = module.service.task_execution_role_name
}

output "task_execution_role_arn" {
  value = data.aws_iam_role.task_execution_role.arn
}
