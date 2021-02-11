package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{
  intField,
  keywordField,
  objectField,
  textField
}
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import uk.ac.wellcome.elasticsearch.IndexedWorkIndexConfig.{
  lowercaseKeyword,
  textWithKeyword
}
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

  def multilingualField(name: String) =
    textWithKeyword(name).fields(
      textField("english").analyzer(englishAnalyzer.name),
      textField("french").analyzer(frenchAnalyzer.name),
      textField("italian").analyzer(italianAnalyzer.name),
      textField("german").analyzer(germanAnalyzer.name),
      textField("hindi").analyzer(hindiAnalyzer.name),
      textField("arabic").analyzer(arabicAnalyzer.name),
      textField("bengali").analyzer(bengaliAnalyzer.name),
      textField("shingles").analyzer(shingleAsciifoldingAnalyzer.name)
    )

  def frenchTextField(name: String) =
    textField(name).fields(
      textField("french").analyzer("french")
    )

  def italianTextField(name: String) =
    textField(name).fields(
      textField("italian").analyzer("italian")
    )

  def germanTextField(name: String) =
    textField(name).fields(
      textField("german").analyzer("german")
    )

  def hindiTextField(name: String) =
    textField(name).fields(
      textField("hindi").analyzer("hindi")
    )

  def arabicTextField(name: String) =
    textField(name).fields(
      textField("arabic").analyzer("arabic")
    )

  def bengaliTextField(name: String) =
    textField(name).fields(
      textField("bengali").analyzer("bengali")
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
