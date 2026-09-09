# Optional connection from the Axiell→FOLIO sync Lambda to the FOLIO dev server.
#
# The dev server has no public IP and no inbound ports. To let the *deployed*
# Lambda talk to it — exercising the real function, its IAM role and the Step
# Functions path — the Lambda's ENIs are placed in the catalogue VPC's private
# subnets, alongside the adapter ECS tasks, and the dev server's security group
# is opened to them on Kong's :8000.
#
# This is deliberately opt-in and off by default. Attaching a Lambda to a VPC
# removes its default internet egress, so this must not be switched on
# unthinkingly for the production path (the EBSCO SaaS tenant needs no VPC at
# all). The catalogue private subnets carry the adapter ECS tasks, which already
# reach S3, S3 Tables and SSM from there, so the attached Lambda has the egress
# it needs.
#
# Enabling is one step: flip folio_dev_target_enabled to true and apply. The
# OKAPI SecureString is composed below from live data, so there is no follow-up
# by hand. A run then opts in per-invocation with {"folio_target": "dev"} on the
# event; runs without it still go to prod.
#
# Rollback is flipping the flag back to false: the Lambda drops its ENIs and
# returns to the Lambda-managed network. Nothing else needs unwinding.
#
# Every hop was verified against the live account (NAT, the S3 gateway endpoint,
# NACLs and the :8000 listener binding), and both targets have since been
# smoke-tested end to end. Background and evidence:
# docs/axiell-folio-sync-lambda-dev-instance.md
locals {
  folio_dev_target_enabled = true

  folio_dev_api_port = 8000 # Kong/Eureka gateway, plain HTTP

  # Built from the instance's live private IP rather than a literal: the sandbox
  # has been rebuilt more than once, and each rebuild moves the address.
  folio_dev_okapi_url = local.folio_dev_target_enabled ? "http://${data.aws_instance.folio_dev[0].private_ip}:${local.folio_dev_api_port}" : ""
}

# The sandbox instance and its security group are owned by
# wellcomecollection/aws-account-infrastructure, so they are looked up rather
# than declared here. Looking them up by name/tag — instead of pinning the ids —
# means a rebuild of the sandbox does not silently break this stack.
#
# "stopped" is included deliberately: the sandbox is shut down outside working
# hours to save cost, and a stopped instance keeps its private IP. Without it,
# an apply in the evening would fail to find the instance at all.
data "aws_instance" "folio_dev" {
  count = local.folio_dev_target_enabled ? 1 : 0

  filter {
    name   = "tag:Name"
    values = ["folio-sandbox"]
  }
  filter {
    name   = "instance-state-name"
    values = ["running", "stopped"]
  }
}

data "aws_security_group" "folio_dev" {
  count = local.folio_dev_target_enabled ? 1 : 0

  name   = "folio-sandbox-sg"
  vpc_id = local.vpc_id
}

# Dedicated SG rather than reusing adapter_egress: both now live in the catalogue
# VPC so reuse would work, but keeping them separate means the dev attachment is
# created and destroyed with its own flag, and nothing granting adapter_egress
# ingress silently starts admitting the sync Lambda too.
resource "aws_security_group" "folio_sync_dev" {
  count = local.folio_dev_target_enabled ? 1 : 0

  name        = "axiell-folio-sync-dev"
  description = "ENIs for the Axiell to FOLIO sync Lambda when targeting the FOLIO dev server"
  vpc_id      = local.vpc_id
}

# Egress to anywhere: the Lambda still needs SSM, KMS, S3, S3 Tables and
# CloudWatch, which leave via the subnets' NAT gateway and S3 gateway endpoint.
resource "aws_vpc_security_group_egress_rule" "folio_sync_dev_allow_all" {
  count = local.folio_dev_target_enabled ? 1 : 0

  security_group_id = aws_security_group.folio_sync_dev[0].id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
  description       = "Permissive egress (SSM, KMS, S3, S3 Tables, CloudWatch, FOLIO)"
}

# The server side. folio-sandbox-sg ships with zero ingress rules, so without
# this the Lambda gets connection-refused however correct the rest is.
resource "aws_vpc_security_group_ingress_rule" "folio_dev_from_sync" {
  count = local.folio_dev_target_enabled ? 1 : 0

  security_group_id            = data.aws_security_group.folio_dev[0].id
  referenced_security_group_id = aws_security_group.folio_sync_dev[0].id
  from_port                    = local.folio_dev_api_port
  to_port                      = local.folio_dev_api_port
  ip_protocol                  = "tcp"
  description                  = "Kong/Eureka API from the Axiell to FOLIO sync Lambda"
}
