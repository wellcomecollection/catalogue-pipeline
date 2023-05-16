
locals {
  full_name = var.name

  mappings_file = var.mappings_name!="empty"?"${path.root}/index_config/mappings.${var.mappings_name}.json": "${path.module}/mappings.empty.json"
  analysis_name = var.analysis_name != ""?var.analysis_name:var.mappings_name

  empty_analysis = {
    analyzer: {}
    normalizer: {}
  }
  analysis_json =  local.analysis_name!="empty" ? merge(local.empty_analysis,jsondecode(file("${path.root}/index_config/analysis.${var.mappings_name}.json"))): local.empty_analysis
}

resource "elasticstack_elasticsearch_index" "the_index" {
  name = local.full_name
  mappings = file(local.mappings_file)
  analysis_analyzer = jsonencode(local.analysis_json["analyzer"])
  analysis_normalizer = jsonencode(local.analysis_json["normalizer"])
}
