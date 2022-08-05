moved {
  from = module.transformer_calm
  to   = module.transformers["calm"].module.transformer
}

moved {
  from = aws_iam_role_policy.calm_transformer_read_adapter_store
  to   = module.transformers["calm"].aws_iam_role_policy.read_adapter_store
}

moved {
  from = module.transformer_calm_output_topic
  to   = module.transformers["calm"].module.output_topic
}

moved {
  from = module.transformer_mets
  to   = module.transformers["mets"].module.transformer
}

moved {
  from = aws_iam_role_policy.mets_transformer_read_adapter_store
  to   = module.transformers["mets"].aws_iam_role_policy.read_adapter_store
}

moved {
  from = module.transformer_mets_output_topic
  to   = module.transformers["mets"].module.output_topic
}

moved {
  from = module.transformer_miro
  to   = module.transformers["miro"].module.transformer
}

moved {
  from = aws_iam_role_policy.miro_transformer_read_adapter_store
  to   = module.transformers["miro"].aws_iam_role_policy.read_adapter_store
}

moved {
  from = module.transformer_miro_output_topic
  to   = module.transformers["miro"].module.output_topic
}

moved {
  from = module.transformer_sierra
  to   = module.transformers["sierra"].module.transformer
}

moved {
  from = aws_iam_role_policy.sierra_transformer_read_adapter_store
  to   = module.transformers["sierra"].aws_iam_role_policy.read_adapter_store
}

moved {
  from = module.transformer_sierra_output_topic
  to   = module.transformers["sierra"].module.output_topic
}

moved {
  from = module.transformer_tei
  to   = module.transformers["tei"].module.transformer
}

moved {
  from = aws_iam_role_policy.tei_transformer_read_adapter_store
  to   = module.transformers["tei"].aws_iam_role_policy.read_adapter_store
}

moved {
  from = module.transformer_tei_output_topic
  to   = module.transformers["tei"].module.output_topic
}

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
  source = "../modules/transformer"

  for_each = local.transformers

  source_name = each.key

  adapter_config      = var.adapter_config[each.key]
  listen_to_reindexer = var.reindexing_state.listen_to_reindexer

  queue_visibility_timeout_seconds = lookup(each.value, "queue_visibility_timeout_seconds", 30)

  cpu    = lookup(each.value, "cpu", 512)
  memory = lookup(each.value, "memory", 1024)

  container_image = each.value["container_image"]

  env_vars = {
    es_index = local.es_works_source_index

    batch_size             = lookup(each.value, "batch_size", 100)
    flush_interval_seconds = lookup(each.value, "flush_interval_seconds", 30)
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["transformer"]

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
