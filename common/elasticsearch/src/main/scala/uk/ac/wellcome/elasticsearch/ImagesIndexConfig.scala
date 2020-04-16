package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping

case object ImagesIndexConfig extends IndexConfig {

  override val analysis: Analysis = Analysis(List())

  val fullText = textField("fullText")
  def englishTextField(name: String) =
    textField(name).fields(
      keywordField("keyword"),
      textField("english").analyzer("english")
    )

  val inferredData = objectField("inferredData").fields(
    denseVectorField("features1", 2048),
    denseVectorField("features2", 2048),
    keywordField("lshEncodedFeatures"))

  override val mapping: MappingDefinition = properties(
    id("id"),
    version,
    location("location"),
    id("parentWork"),
    fullText,
    inferredData
  ).dynamic(DynamicMapping.Strict)
}
