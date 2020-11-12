locals {
  enable_sierra_reindexing = var.enable_reindexing || var.enable_sierra_reindexing
  enable_miro_reindexing   = var.enable_reindexing || var.enable_miro_reindexing
  enable_calm_reindexing   = var.enable_reindexing || var.enable_calm_reindexing
  enable_mets_reindexing   = var.enable_reindexing || var.enable_mets_reindexing

  sierra_topics = (
    local.enable_sierra_reindexing
    ? concat(var.sierra_adapter_topic_arns["updates_topics"], [var.sierra_adapter_topic_arns["reindexer_topic"]])
    : concat(var.sierra_adapter_topic_arns["updates_topics"], [])
  )

  miro_topics = (
    local.enable_miro_reindexing
    ? concat(var.miro_adapter_topic_arns["updates_topics"], [var.miro_adapter_topic_arns["reindexer_topic"]])
    : concat(var.miro_adapter_topic_arns["updates_topics"], [])
  )

  calm_topics = (
    local.enable_calm_reindexing
    ? concat(var.calm_adapter_topic_arns["updates_topics"], [var.calm_adapter_topic_arns["reindexer_topic"]])
    : concat(var.calm_adapter_topic_arns["updates_topics"], [])
  )

  mets_topics = (
    local.enable_mets_reindexing
    ? concat(var.mets_adapter_topic_arns["updates_topics"], [var.mets_adapter_topic_arns["reindexer_topic"]])
    : concat(var.mets_adapter_topic_arns["updates_topics"], [])
  )
}
