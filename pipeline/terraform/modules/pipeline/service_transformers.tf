locals {
  transformers = {
    calm = {
      container_image = local.transformer_calm_image
    }

    mets = {
      container_image = local.transformer_mets_image

      # The default visibility timeout is 30 seconds, and occasionally we see
      # works get sent to the DLQ that still got through the transformer --
      # presumably because they took a bit too long to process.
      #
      # Bumping the timeout is an attempt to avoid the messages being
      # sent to a DLQ.
      queue_visibility_timeout_seconds = 90

      # The METS transformer is quite CPU intensive, and if it doesn't
      # have enough CPU, the Akka scheduler gets resource-starved and
      # the whole app stops doing anything.
      cpu    = 2048
      memory = 4096
    }

    miro = {
      container_image = local.transformer_miro_image

      cpu    = 1024
      memory = 2048
    }

    sierra = {
      container_image = local.transformer_sierra_image
    }

    tei = {
      container_image = local.transformer_tei_image

      # The default visibility timeout is 30 seconds, and occasionally we see
      # works get sent to the DLQ that still got through the transformer --
      # presumably because they took a bit too long to process.
      #
      # Bumping the timeout is an attempt to avoid the messages being
      # sent to a DLQ.
      queue_visibility_timeout_seconds = 90
    }
  }

  transformer_output_topic_arns = [
    for k, v in module.transformers : v.output_topic_arn
  ]
}

module "transformers" {
  source = "../transformer"

  for_each = local.transformers

  source_name = each.key

  adapter_config      = local.adapter_config[each.key]
  listen_to_reindexer = var.reindexing_state.listen_to_reindexer

  queue_visibility_timeout_seconds = lookup(each.value, "queue_visibility_timeout_seconds", 30)

  cpu    = lookup(each.value, "cpu", 512)
  memory = lookup(each.value, "memory", 1024)

  container_image = each.value["container_image"]

  env_vars = {
    es_index = local.es_works_source_index

    batch_size             = lookup(each.value, "batch_size", 100)
    flush_interval_seconds = lookup(each.value, "flush_interval_seconds", 30)
    api_key_version        = module.elastic.api_key_versions["transformer"]
  }

  secret_env_vars = module.elastic.pipeline_storage_es_service_secrets["transformer"]

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
