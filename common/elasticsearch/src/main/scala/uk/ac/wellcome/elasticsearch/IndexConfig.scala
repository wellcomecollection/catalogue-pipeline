package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{
  intField,
  keywordField,
  objectField,
  textField
}
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

  val languagesTextFields =
    languages.map(lang => textField(lang).analyzer(s"${lang}_analyzer"))

  def multilingualField(name: String) =
    textField(name)
      .fields(
        List(
          textField("english").analyzer(englishAnalyzer.name),
          textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)) ++
          languagesTextFields,
      )

  def multilingualKeywordField(name: String) = textField(name).fields(
    lowercaseKeyword("keyword"),
    // we don't care about the name, we just want to compose the fields parameter
    multilingualField("").fields: _*
  )

  def lowercaseKeyword(name: String) =
    keywordField(name).normalizer(lowercaseNormalizer.name)

  def asciifoldingTextFieldWithKeyword(name: String) =
    textWithKeyword(name)
      .analyzer(asciifoldingAnalyzer.name)

  val label = asciifoldingTextFieldWithKeyword("label")

  val canonicalId = lowercaseKeyword("canonicalId")

  val version = intField("version")

  val sourceIdentifier = objectField("sourceIdentifier")
    .fields(lowercaseKeyword("value"))
    .dynamic("false")
}

object NoStrictMapping extends IndexConfig {
  val analysis: Analysis = Analysis(analyzers = List())
  val mapping: MappingDefinition = MappingDefinition.empty
}
