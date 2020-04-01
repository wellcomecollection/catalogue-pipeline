package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

case object ImagesIndexConfig extends IndexConfig {

  override val analysis: Analysis = Analysis(List())
  // This encodes how someone would expect the field to work, but allow querying it in other ways.
  def textWithKeyword(name: String) =
    textField(name).fields(keywordField("keyword"))

  def keywordWithText(name: String) =
    keywordField(name).fields(textField("text"))

  val label = textWithKeyword("label")

  val sourceIdentifierValue = keywordWithText("value")

  val canonicalId = keywordWithText("canonicalId")
  val fullText = textField("fullText")
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
        dateField("to"),
        objectField("status").fields(keywordField("type"))
      )

  val license = objectField("license").fields(
    keywordField("id")
  )
  val inferredData = objectField("inferredData").fields(floatField("features1"), floatField("features2"), keywordField("lshEncodedFeatures"))

  override val mapping: MappingDefinition = properties(
    id("id"),
    intField("version"),
    location("location"),
    id("parentWork"),
    fullText,
    inferredData
  ).dynamic(DynamicMapping.Strict)
}
