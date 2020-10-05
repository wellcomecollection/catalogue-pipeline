package uk.ac.wellcome.platform.snapshot_generator.akka.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl.{search, termQuery}
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import grizzled.slf4j.Logging

import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.work.internal._
import WorkState.Identified
import uk.ac.wellcome.models.Implicits._

object ElasticsearchWorksSource extends Logging {
  def apply(elasticClient: ElasticClient, index: Index)(
    implicit
      actorSystem: ActorSystem
  ): Source[Work[Identified], NotUsed] = {
    val loggingSink = Flow[Work[Identified]]
      .grouped(10000)
      .map(works => {
        logger.info(s"Received ${works.length} works from $index")
        works
      })
      .to(Sink.ignore)
    Source
      .fromPublisher(
        elasticClient.publisher(
          search(index)
            .query(termQuery("type", "Visible"))
            .scroll(keepAlive = "5m")
            // Increasing the size of each request from the
            // default 100 to 1000 as it makes it go significantly faster
            .size(1000))
      )
      .map { searchHit: SearchHit =>
        fromJson[Work[Identified]](searchHit.sourceAsString).get
      }
      .alsoTo(loggingSink)
  }
}
