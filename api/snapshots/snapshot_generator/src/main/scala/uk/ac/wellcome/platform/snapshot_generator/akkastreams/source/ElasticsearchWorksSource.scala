package uk.ac.wellcome.platform.snapshot_generator.akkastreams.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl.{search, termQuery}
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.snapshot_generator.models.SnapshotGeneratorConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Indexed

import java.text.NumberFormat

object ElasticsearchWorksSource extends Logging {
  def apply(elasticClient: ElasticClient,
            snapshotConfig: SnapshotGeneratorConfig)(
    implicit
    actorSystem: ActorSystem
  ): Source[Work[Indexed], NotUsed] = {
    val loggingSink = Flow[Work[Indexed]].zipWithIndex
      .map { case (_, index) => index + 1 }
      .grouped(10000)
      .map(indices =>
        info(s"Received another ${intComma(indices.length)} works (${intComma(
          indices.max)} so far) from ${snapshotConfig.index}"))
      .to(Sink.ignore)

    Source
      .fromPublisher(
        elasticClient.publisher(
          search(snapshotConfig.index)
            .query(termQuery("type", "Visible"))
            .scroll(keepAlive = "5m")
            .size(snapshotConfig.bulkSize))
      )
      .map { searchHit: SearchHit =>
        fromJson[Work[Indexed]](searchHit.sourceAsString).get
      }
      .alsoTo(loggingSink)
  }

  private def intComma(number: Long): String =
    NumberFormat.getInstance().format(number)
}
