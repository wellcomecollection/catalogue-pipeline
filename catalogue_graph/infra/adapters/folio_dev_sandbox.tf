# Connects the Axiell to FOLIO sync Lambda to the FOLIO dev server. Everything
# here is gated by folio_dev_target_enabled.
#
# When it is enabled, the Lambda's ENIs go in the catalogue VPC private subnets
# and folio-sandbox-sg accepts them on Kong's :8000. Setting the flag back to
# false removes both, and the Lambda returns to the Lambda-managed network.
#
# The OKAPI SecureString for the sandbox is created as a placeholder by
# modules/axiell_folio_sync/ssm.tf, and populated by
# scripts/folio_dev_session.sh. A run reaches the sandbox by passing
# {"folio_target": "dev"} on the event. Runs without it go to prod.
#
# See docs/axiell-folio-sync-lambda-dev-instance.md.
locals {
  folio_dev_target_enabled = true

  folio_dev_api_port = 8000 # Kong/Eureka gateway, plain HTTP
}

# The sandbox's security group, owned by
# wellcomecollection/aws-account-infrastructure and matched by name.
data "aws_security_group" "folio_dev" {
  count = local.folio_dev_target_enabled ? 1 : 0

  name   = "folio-sandbox-sg"
  vpc_id = local.vpc_id
}

# Security group for the sync Lambda's ENIs.
resource "aws_security_group" "folio_sync_dev" {
  count = local.folio_dev_target_enabled ? 1 : 0

  name        = "axiell-folio-sync-dev"
  description = "ENIs for the Axiell to FOLIO sync Lambda when targeting the FOLIO dev server"
  vpc_id      = local.vpc_id
}

# Allow-all egress, covering SSM, KMS, S3, S3 Tables, CloudWatch and FOLIO.
resource "aws_vpc_security_group_egress_rule" "folio_sync_dev_allow_all" {
  count = local.folio_dev_target_enabled ? 1 : 0

  security_group_id = aws_security_group.folio_sync_dev[0].id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
  description       = "Permissive egress (SSM, KMS, S3, S3 Tables, CloudWatch, FOLIO)"
}

# The only ingress rule on folio-sandbox-sg. It allows the sync Lambda's ENIs to
# reach Kong on :8000.
resource "aws_vpc_security_group_ingress_rule" "folio_dev_from_sync" {
  count = local.folio_dev_target_enabled ? 1 : 0

  security_group_id            = data.aws_security_group.folio_dev[0].id
  referenced_security_group_id = aws_security_group.folio_sync_dev[0].id
  from_port                    = local.folio_dev_api_port
  to_port                      = local.folio_dev_api_port
  ip_protocol                  = "tcp"
  description                  = "Kong/Eureka API from the Axiell to FOLIO sync Lambda"
}
