package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{keywordField, _}
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.{
  FieldDefinition,
  MappingDefinition,
  ObjectField
}
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import io.circe.Decoder.state

object ImagesIndexConfig extends IndexConfig with WorksIndexConfigFields {

  val analysis: Analysis = WorksAnalysis()

  val inferredData = objectField("inferredData").fields(
    denseVectorField("features1", 2048),
    denseVectorField("features2", 2048),
    keywordField("lshEncodedFeatures"),
    keywordField("palette")
  )

  private def sourceWork(fieldName: String): ObjectField =
    objectField(fieldName).fields(
      id(),
      data(
        pathField = textField("path"),
        imageState = identifiedSourceImageState
      ),
      keywordField("type"),
      version
    )

  val source = objectField("source").fields(
    sourceWork("canonicalWork"),
    sourceWork("redirectedWork"),
    keywordField("type")
  )

  val augmentedState = objectField("state").fields(
    sourceIdentifier,
    canonicalId,
    modifiedTime,
    source,
    inferredData
  )

  def mapping: MappingDefinition =
    properties(
      version,
      augmentedState,
      location("locations")
    ).dynamic(DynamicMapping.Strict)
}
