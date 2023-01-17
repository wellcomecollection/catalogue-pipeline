locals {
  topic_arns = var.listen_to_reindexer ? concat(var.adapter_config.topics, [var.adapter_config.reindex_topic]) : var.adapter_config.topics

  namespace = var.fargate_service_boilerplate.namespace
}
