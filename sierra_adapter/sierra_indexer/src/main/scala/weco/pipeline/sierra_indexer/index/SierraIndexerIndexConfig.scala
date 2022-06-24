package weco.pipeline.sierra_indexer.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.fields.{ElasticField, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import weco.elasticsearch.IndexConfig
import weco.catalogue.internal_model.index.IndexConfigFields

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

  def indexer: IndexConfig =
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
            textKeywordField(name = "content", textFieldName = "english", analyzerName = "english")
          ),
          textKeywordField(name = "content", textFieldName = "english", analyzerName = "english")
        )
      )
    )

  def varfield: IndexConfig =
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
            textKeywordField(name = "content", textFieldName = "english", analyzerName = "english")
          ),
          textKeywordField(name = "content", textFieldName = "english", analyzerName = "english")
        )
      )
    )

  def fixedField: IndexConfig =
    SierraIndexConfig(
      Seq(
        parent,
        keywordField("code"),
        objectField("fixedField").fields(
          keywordField("label"),
          textKeywordField(name = "display", textFieldName = "english", analyzerName = "english"),
          textKeywordField(name = "value", textFieldName = "english", analyzerName = "english"),
        )
      )
    )
}
