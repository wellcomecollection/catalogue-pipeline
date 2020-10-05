output "cluster_arn" {
  value = aws_ecs_cluster.catalogue_api.arn
}

output "nlb_arn" {
  value = aws_lb.catalogue_api.arn
}

output "api_gateway_id" {
  value = aws_api_gateway_rest_api.catalogue_api.id
}

output "api_gateway_name" {
  value = aws_api_gateway_rest_api.catalogue_api.name
}

output "certificate_arn" {
  value = aws_acm_certificate_validation.catalogue_api_validation.certificate_arn
}

output "egress_security_group_id" {
  value = aws_security_group.egress.id
}

output "interservice_security_group_id" {
  value = aws_security_group.interservice.id
}

output "service_lb_ingress_security_group_id" {
  value = aws_security_group.service_lb_ingress_security_group.id
}

output "staging_namespace" {
  value = aws_service_discovery_private_dns_namespace.staging_namespace.id
}

output "prod_namespace" {
  value = aws_service_discovery_private_dns_namespace.prod_namespace.id
}

output "ecr_api_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "ecr_snapshot_generator_repository_url" {
  value = aws_ecr_repository.snapshot_generator.repository_url
}
