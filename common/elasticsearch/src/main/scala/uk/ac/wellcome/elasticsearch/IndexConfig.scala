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

  def sourceIdentifierFields = Seq(
    keywordField("ontologyType"),
    objectField("identifierType").fields(
      label,
      keywordField("id")
    ),
    sourceIdentifierValue
  )

  def id(fieldName: String = "id") =
    objectField(fieldName).fields(
      keywordField("type"),
      canonicalId,
      objectField("sourceIdentifier").fields(sourceIdentifierFields),
      objectField("otherIdentifiers").fields(sourceIdentifierFields)
    )

  def location(fieldName: String = "locations") =
    objectField(fieldName).fields(
      keywordField("type"),
      objectField("locationType").fields(
        label,
        keywordField("id")
      ),
      label,
      textField("url"),
      textField("credit"),
      license,
      accessConditions
    )

  val accessConditions =
    objectField("accessConditions")
      .fields(
        englishTextKeywordField("terms"),
        textField("to"),
        objectField("status").fields(keywordField("type"))
      )

  val license = objectField("license").fields(
    keywordField("id")
  )

  val label = asciifoldingTextFieldWithKeyword("label")

  val sourceIdentifierValue = lowercaseKeyword("value")

  val canonicalId = lowercaseKeyword("canonicalId")
  val version = intField("version")
}
