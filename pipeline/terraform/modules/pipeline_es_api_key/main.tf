locals {
  read_descriptor = {
    indices = [
      {
        # Set explicitly: the provider reports the default, so omitting it causes a perpetual diff.
        allow_restricted_indices = false,
        names                    = var.read_from
        privileges               = ["read"]
      }
    ]
  }

  write_descriptor = {
    indices = [
      {
        allow_restricted_indices = false,
        names                    = var.write_to
        privileges               = ["all"]
      }
    ]
  }

  role_descriptors = length(var.write_to) > 0 && length(var.read_from) > 0 ? {
    read  = local.read_descriptor
    write = local.write_descriptor
    } : (length(var.read_from) > 0 ?
    {
      read = local.read_descriptor
      } : {
      write = local.write_descriptor
  })
}

resource "elasticstack_elasticsearch_security_api_key" "pipeline_service" {
  name             = "${var.name}-${var.pipeline_date}"
  role_descriptors = jsonencode(local.role_descriptors)

  # On replacement, create the new key and store it in Secrets Manager before
  # invalidating the old one, so consumers that re-read the secret (e.g. the
  # catalogue API on a 401) can recover without a redeploy (platform#6528).
  lifecycle {
    create_before_destroy = true
  }
}

module "pipeline_service_api_key_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.5.0"

  deletion_mode = "IMMEDIATE"

  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/${var.name}/api_key" = elasticstack_elasticsearch_security_api_key.pipeline_service.encoded
  }
}

module "pipeline_catalogue_service_api_key_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.5.0"

  count = var.expose_to_catalogue ? 1 : 0

  providers = {
    aws = aws.catalogue
  }

  deletion_mode = "IMMEDIATE"
  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/${var.name}/api_key" = elasticstack_elasticsearch_security_api_key.pipeline_service.encoded
  }
}
