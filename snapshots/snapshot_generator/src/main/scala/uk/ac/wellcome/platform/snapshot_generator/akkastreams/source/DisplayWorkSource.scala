package uk.ac.wellcome.platform.snapshot_generator.akkastreams.source

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.ElasticClient
import uk.ac.wellcome.display.models.{DisplayWork, WorksIncludes}
import uk.ac.wellcome.platform.snapshot_generator.akkastreams.flow.IndexedWorkToVisibleDisplayWork
import uk.ac.wellcome.platform.snapshot_generator.models.SnapshotGeneratorConfig

object DisplayWorkSource {
  def apply(
    elasticClient: ElasticClient,
    snapshotConfig: SnapshotGeneratorConfig
  )(implicit actorSystem: ActorSystem): Source[DisplayWork, Any] =
    ElasticsearchWorksSource(
      elasticClient = elasticClient,
      snapshotConfig = snapshotConfig)
      .via(IndexedWorkToVisibleDisplayWork(DisplayWork(_, WorksIncludes.all)))
}
