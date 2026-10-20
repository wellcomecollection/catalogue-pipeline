output "project" {
  value = {
    id          = module.project.project_id
    endpoints   = module.project.endpoints
    search_lake = module.project.search_lake
  }
}

output "harness_lambda_arn" {
  value = module.harness_lambda.lambda_arn
}

output "vpc_endpoint" {
  value = {
    id       = aws_vpc_endpoint.serverless.id
    dns_name = aws_vpc_endpoint.serverless.dns_entry[0]["dns_name"]
  }
}

output "hosted_comparator_index" {
  value = module.hosted_comparator_index.name
}
