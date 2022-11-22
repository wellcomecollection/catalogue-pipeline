package weco.catalogue.internal_model.index

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import weco.elasticsearch.IndexConfig

import scala.io.Source

object ImagesIndexConfig extends IndexConfigFields {
  val analysis = WorksAnalysis()
  val emptyDynamicFalseMapping = properties(Seq()).dynamic(DynamicMapping.False)
  val initial = IndexConfig(emptyDynamicFalseMapping, analysis)
  val augmented = IndexConfig(emptyDynamicFalseMapping, analysis)

  def getResourceAsString(resourceURL: String): String =
    Source
      .fromInputStream(
        getClass.getResourceAsStream(resourceURL)
      )
      .mkString

  val indexed = IndexConfig(
    {
      val mapping = {
        MappingDefinition(
          rawSource = Some(
            IndexMapping(
              propertiesJson =
                getResourceAsString("/imagesIndexProperties.json")
            )
          )
        )
      }
      mapping
    },
    analysis
  )
}
