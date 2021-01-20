package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{keywordField, _}
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.{MappingDefinition, ObjectField}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

object ImagesIndexConfig extends IndexConfig with WorksIndexConfigFields {

  val analysis: Analysis = WorksAnalysis()

  val inferredData = objectField("inferredData").fields(
    denseVectorField("features1", 2048),
    denseVectorField("features2", 2048),
    keywordField("lshEncodedFeatures"),
    keywordField("palette"),
    intField("binSizes").index(false),
    floatField("binMinima").index(false)
  )

  val derivedImageData = objectField("derivedData").fields(
    location("thumbnail")
  )

  private def sourceWork(fieldName: String): ObjectField =
    objectField(fieldName).fields(
      id(),
      data(pathField = textField("path")),
      keywordField("type"),
      version
    )

  val source = objectField("source").fields(
    sourceWork("canonicalWork"),
    sourceWork("redirectedWork"),
    keywordField("type")
  )

  val indexedState = objectField("state").fields(
    sourceIdentifier,
    canonicalId,
    inferredData,
    derivedImageData
  )

  def mapping: MappingDefinition =
    properties(
      version,
      modifiedTime,
      source,
      indexedState,
      location("locations")
    ).dynamic(DynamicMapping.Strict)
}
