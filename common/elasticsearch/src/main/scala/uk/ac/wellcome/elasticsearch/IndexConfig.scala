package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition

trait IndexConfig {
  val mapping: MappingDefinition
  val analysis: Analysis
}

