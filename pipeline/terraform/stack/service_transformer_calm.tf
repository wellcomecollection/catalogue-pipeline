moved {
  from = module.transformer_calm
  to   = module.transformers["calm"].module.transformer
}

moved {
  from = aws_iam_role_policy.calm_transformer_read_adapter_store
  to   = module.transformers["calm"].aws_iam_role_policy.read_adapter_store
}

moved {
  from = aws_iam_role_policy.transformer_calm_output_topic
  to   = module.transformers["calm"].aws_iam_role_policy.output_topic
}

locals {
  transformers = {
    calm = {
      container_image = local.transformer_calm_image

      batch_size             = 100
      flush_interval_seconds = 30
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

  container_image = each.value["container_image"]

  env_vars = {
    es_works_source_index = local.es_works_source_index

    batch_size             = each.value["batch_size"]
    flush_interval_seconds = each.value["flush_interval_seconds"]
  }

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
