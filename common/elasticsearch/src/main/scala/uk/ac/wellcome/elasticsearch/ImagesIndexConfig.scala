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
    keywordField("palette")
  )

  private def sourceWork(fieldName: String): ObjectField =
    objectField(fieldName)
      .fields(
        objectField("id").fields(
          canonicalId,
          objectField("sourceIdentifier").fields(lowercaseKeyword("value")),
        ),
        data,
        keywordField("type"),
      ).dynamic("false")

  val source = objectField("source").fields(
    sourceWork("canonicalWork"),
    sourceWork("redirectedWork"),
    keywordField("type")
  )

  val indexedState = objectField("state")
    .fields(
      objectField("sourceIdentifier")
        .fields(lowercaseKeyword("value"))
        .dynamic("false"),
      canonicalId,
      inferredData,
    )

  def mapping: MappingDefinition =
    properties(
      intField("version"),
      dateField("modifiedTime"),
      source,
      indexedState,
      objectField("locations").dynamic("false")
    ).dynamic(DynamicMapping.Strict)
}
