# RDS

output "rds_access_security_group_id" {
  value = "${aws_security_group.rds_ingress_security_group.id}"
}
