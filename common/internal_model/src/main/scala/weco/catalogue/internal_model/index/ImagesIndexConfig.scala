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

  val indexed = IndexConfig(
    {
      // Here we set dynamic strict to be sure the object vaguely looks like an
      // image and contains the core fields, adding DynamicMapping.False in places
      // where we do not need to map every field and can save CPU.
      val mapping = {
        MappingDefinition(
          rawSource = Some(
            IndexMapping(
              propertiesJson = Source
                .fromInputStream(
                  getClass.getResourceAsStream("/imagesIndexProperties.json")
                )
                .mkString,
              buildVersion = BuildInfo.version
            )
          )
        )
      }
      mapping
    },
    analysis
  )
}
