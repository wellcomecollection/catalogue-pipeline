locals {
  full_name = var.name

  mappings_file = var.mappings_name != "empty" ? "${var.config_path}/mappings.${var.mappings_name}.json" : "${path.module}/mappings.empty.json"
  analysis_name = var.analysis_name != "" ? var.analysis_name : var.mappings_name

  analysis_json = jsondecode(local.analysis_name != "empty"?file("${var.config_path}/analysis.${local.analysis_name}.json"):"{}")

  analysis = {
    analyzer: try(jsonencode(local.analysis_json["analyzer"]), "{}")
    normalizer: try(jsonencode(local.analysis_json["normalizer"]), "{}")
    filter: try(jsonencode(local.analysis_json["filter"]), "{}")
    char_filter: try(jsonencode(local.analysis_json["char_filter"]), "{}")
    tokenizer: try(jsonencode(local.analysis_json["tokenizer"]), "{}")
  }
}

resource "elasticstack_elasticsearch_index" "the_index" {
  name                = local.full_name
  mappings            = file(local.mappings_file)
  analysis_analyzer   = local.analysis.analyzer
  analysis_normalizer = local.analysis.normalizer
  analysis_filter = local.analysis.filter
  analysis_char_filter = local.analysis.char_filter
  analysis_tokenizer = local.analysis.tokenizer
}
