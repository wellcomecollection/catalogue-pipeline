package weco.catalogue.sierra_indexer.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.models.index.IndexConfigFields

trait SierraIndexerIndexConfig extends IndexConfig with IndexConfigFields {
  override def analysis: Analysis =
    Analysis(analyzers = List())
}

object VarfieldIndexConfig extends SierraIndexerIndexConfig {
  val fields = Seq(
    objectField("parent").fields(
      keywordField("id"),
      keywordField("idWithCheckDigit"),
      keywordField("recordType")
    ),
    intField("position"),
    objectField("varField").fields(
      keywordField("fieldTag"),
      keywordField("ind1"),
      keywordField("ind2"),
      keywordField("marcTag"),
      objectField("subfields").fields(
        keywordField("tag"),
        englishTextKeywordField("content")
      ),
      englishTextKeywordField("content")
    )
  )

  override def mapping: MappingDefinition =
    properties(fields).dynamic(DynamicMapping.Strict)
}
