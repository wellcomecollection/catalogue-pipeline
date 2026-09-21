# Connects the Axiell-to-FOLIO sync Lambda to the FOLIO dev server.
#
# When enabled, the Lambda uses catalogue VPC private subnets and reaches
# Kong on :8000. When disabled, it returns to Lambda-managed networking.
#
# The sandbox OKAPI SecureString is created as a placeholder by
# modules/axiell_folio_sync/ssm.tf and filled in by hand. Pass
# {"folio_target": "dev"} to target the sandbox; otherwise runs use prod.
#
# See docs/axiell-folio-sync-lambda-dev-instance.md.
locals {
  # A local, not a variable: this root is applied by several people, and an
  # apply that omits a -var would destroy the hand-filled dev SecureString.
  folio_dev_target_enabled = true
}

# Security group for the sync Lambda's ENIs.
#
# Kept outside folio_dev_target_enabled because Lambda deletes Hyperplane ENIs
# asynchronously. Destroying this group during detachment causes
# DependencyViolation; keeping it also preserves the id used by folio-dev-server.
#
# When disabled, nothing is attached and the empty group has no cost.
#
# The matching ingress rule is managed by
# wellcomecollection/folio-dev-server. Pass this id to
# sync_lambda_security_group_id to allow Kong's :8000 access.
resource "aws_security_group" "folio_sync_dev" {
  name        = "axiell-folio-sync-dev"
  description = "ENIs for the Axiell to FOLIO sync Lambda when targeting the FOLIO dev server"
  vpc_id      = local.vpc_id
}

# Allow egress for SSM, KMS, S3, S3 Tables, CloudWatch, and FOLIO.
resource "aws_vpc_security_group_egress_rule" "folio_sync_dev_allow_all" {
  security_group_id = aws_security_group.folio_sync_dev.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
  description       = "Permissive egress (SSM, KMS, S3, S3 Tables, CloudWatch, FOLIO)"
}

# Used by folio-dev-server's sync_lambda_security_group_id; stable across toggles.
output "axiell_folio_sync_dev_security_group_id" {
  description = "Security group of the sync Lambda's ENIs, to be allowed inbound on Kong's :8000 by folio-dev-server"
  value       = aws_security_group.folio_sync_dev.id
}
