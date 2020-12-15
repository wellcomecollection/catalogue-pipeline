package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{keywordField, textField}
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import uk.ac.wellcome.elasticsearch.WorksAnalysis._

trait IndexConfig {
  def mapping: MappingDefinition
  def analysis: Analysis
  def shards: Int = 1
}

/** Mixin for common fields used within an IndexConfig in our internal models.
  */
trait IndexConfigFields {
  // `textWithKeyword` and `keywordWithText` are slightly different in the semantics and their use case.
  // If the intended field type is keyword, but you would like to search it textually, use `keywordWithText` and
  // visa versa.

  // This encodes how someone would expect the field to work, but allow querying it in other ways.
  def textWithKeyword(name: String) =
    textField(name).fields(keywordField("keyword"))

  def keywordWithText(name: String) =
    keywordField(name).fields(textField("text"))

  def englishTextKeywordField(name: String) =
    textField(name).fields(
      keywordField("keyword"),
      textField("english").analyzer("english")
    )

  def englishTextField(name: String) =
    textField(name).fields(
      textField("english").analyzer("english")
    )

  def lowercaseKeyword(name: String) =
    keywordField(name).normalizer(lowercaseNormalizer.name)

  def asciifoldingTextFieldWithKeyword(name: String) =
    textWithKeyword(name)
      .analyzer(asciifoldingAnalyzer.name)

  val label = asciifoldingTextFieldWithKeyword("label")

  val canonicalId = lowercaseKeyword("canonicalId")
}

object NoStrictMapping extends IndexConfig {
  val analysis: Analysis = Analysis(analyzers = List())
  val mapping: MappingDefinition = MappingDefinition.empty
}
