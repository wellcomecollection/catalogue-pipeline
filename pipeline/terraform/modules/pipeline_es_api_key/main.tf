locals {
  read_descriptor = {
    indices = [
      {
        # allow_restricted_indices is false by default, but if you leave it out,
        # terraform will recreate a new API key every time, because it compares
        # the value reported by ElasticStack (which contains this default value)
        # with the one you are trying to insert (which, in that case, would not).
        # This then breaks all of your running pipeline applications because they
        # are still trying to authenticate with the old key.
        #
        # This could (and possibly also should) be avoided by the careful application
        # of replace_triggered_by in the corresponding modules, but it is unlikely
        # that that corresponds to an actual use case we would have in a running pipeline.
        # We don't normally want to regenerate keys in a running pipeline, but we
        # do always terraform a pipeline twice, and would hope to be able to do so
        # without breaking anything.
        #
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
}

module "pipeline_service_api_key_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=add-versions-to-outputs"

  deletion_mode = "IMMEDIATE"

  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/${var.name}/api_key" = elasticstack_elasticsearch_security_api_key.pipeline_service.encoded
  }
}

module "pipeline_catalogue_service_api_key_secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=add-versions-to-outputs"

  count = var.expose_to_catalogue ? 1 : 0

  providers = {
    aws = aws.catalogue
  }

  deletion_mode = "IMMEDIATE"
  key_value_map = {
    "elasticsearch/pipeline_storage_${var.pipeline_date}/${var.name}/api_key" = elasticstack_elasticsearch_security_api_key.pipeline_service.encoded
  }
}
