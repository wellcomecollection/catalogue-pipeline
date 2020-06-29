package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{intField, keywordField, objectField, textField}
import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
trait IndexConfig {
  val mapping: MappingDefinition
  val analysis: Analysis

  // `textWithKeyword` and `keywordWithText` are slightly different in the semantics and their use case.
  // If the intended field type is keyword, but you would like to search it textually, use `keywordWithText` and
  // visa versa.

  // This encodes how someone would expect the field to work, but allow querying it in other ways.
  def textWithKeyword(name: String) =
    textField(name).fields(keywordField("keyword"))

  def keywordWithText(name: String) =
    keywordField(name).fields(textField("text"))

  def englishTextField(name: String) =
    textField(name).fields(
      keywordField("keyword"),
      textField("english").analyzer("english")
    )

  def sourceIdentifierFields = Seq(
    keywordField("ontologyType"),
    objectField("identifierType").fields(
      label,
      keywordField("id"),
      keywordField("ontologyType")
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
      keywordField("ontologyType"),
      objectField("locationType").fields(
        label,
        keywordField("id"),
        keywordField("ontologyType")
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
        englishTextField("terms"),
        textField("to"),
        objectField("status").fields(keywordField("type"))
      )

  val license = objectField("license").fields(
    keywordField("id")
  )

  val label = textWithKeyword("label")

  val sourceIdentifierValue = keywordWithText("value")

  val canonicalId = keywordWithText("canonicalId")
  val version = intField("version")
}

