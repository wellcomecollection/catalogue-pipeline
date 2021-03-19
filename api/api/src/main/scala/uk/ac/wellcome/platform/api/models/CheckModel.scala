package uk.ac.wellcome.platform.api.models

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.Await
import scala.concurrent.duration._

object CheckModel {
  def checkModel(indexName: String)(elasticClient: ElasticClient) = {
    val version = BuildInfo.version.split("\\.").toList
    val mapping =
      Await.result(elasticClient.execute(getMapping(indexName)), 5 seconds)

    val metadata = mapping.result.head.meta

    require(
      metadata.contains(s"model.versions.${version.head}"),
      s"The index $indexName doesn't support internal model version ${BuildInfo.version} (supports $metadata)"
    )
    require(
      metadata(s"model.versions.${version.head}") == version(1),
      s"The index $indexName has a different hash for internal model version ${BuildInfo.version} ($metadata)"
    )
  }
}
