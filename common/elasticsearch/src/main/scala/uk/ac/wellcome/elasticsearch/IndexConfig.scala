package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition

trait IndexConfig {
  def mapping: MappingDefinition
  def analysis: Analysis
  def shards: Int = 1
}

object NoStrictMapping extends IndexConfig {
  val analysis: Analysis = Analysis(analyzers = List())
  val mapping: MappingDefinition = MappingDefinition.empty
}
