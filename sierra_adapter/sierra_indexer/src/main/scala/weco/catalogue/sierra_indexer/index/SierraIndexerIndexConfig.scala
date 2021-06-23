package weco.catalogue.sierra_indexer.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.fields.{ElasticField, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.models.index.IndexConfigFields

object SierraIndexConfig extends IndexConfigFields {
  def apply(fields: Seq[ElasticField]): IndexConfig =
    IndexConfig(
      properties(fields).dynamic(DynamicMapping.Strict),
      Analysis(analyzers = List())
    )

  val parent: ObjectField = objectField("parent").fields(
    keywordField("id"),
    keywordField("idWithCheckDigit"),
    keywordField("recordType")
  )

  def indexer =
    SierraIndexConfig(
      Seq(
        parent,
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
    )

  def varfield =
    SierraIndexConfig(
      Seq(
        parent,
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
    )

  def fixedField =
    SierraIndexConfig(
      Seq(
        parent,
        keywordField("code"),
        objectField("fixedField").fields(
          keywordField("label"),
          englishTextKeywordField("display"),
          englishTextKeywordField("value")
        )
      )
    )
}
