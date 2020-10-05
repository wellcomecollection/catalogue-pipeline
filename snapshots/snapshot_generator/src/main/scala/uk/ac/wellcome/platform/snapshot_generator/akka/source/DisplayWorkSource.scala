package uk.ac.wellcome.platform.snapshot_generator.akka.source

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.display.models.{DisplayWork, WorksIncludes}
import uk.ac.wellcome.platform.snapshot_generator.akka.flow.IdentifiedWorkToVisibleDisplayWork

object DisplayWorkSource {
  def apply(
    elasticClient: ElasticClient,
    index: Index
  )(implicit actorSystem: ActorSystem): Source[DisplayWork, Any] =
    ElasticsearchWorksSource(elasticClient = elasticClient, index = index)
      .via(
        IdentifiedWorkToVisibleDisplayWork(
          DisplayWork(_, WorksIncludes.includeAll())))
}
