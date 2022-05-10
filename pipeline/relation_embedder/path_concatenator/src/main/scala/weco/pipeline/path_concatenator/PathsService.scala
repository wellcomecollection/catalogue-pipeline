package weco.pipeline.path_concatenator

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import akka.actor.ActorSystem
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.json.JsonUtil.exportDecoder

case class ParentPathData(collectionPath: CollectionPath);
case class ParentPathState();
case class PathHit(
  data: ParentPathData
)

class PathsService(elasticClient: ElasticClient, index: Index)(
  implicit as: ActorSystem)
    extends Logging {
  private val requestBuilder = new PathConcatenatorRequestBuilder(index)

  /**
    * Add a scroll to the given request and return an Elasticsearch Publisher
    *
    * The requests made by this service are expected to yield very few results
    * getParentPath and getWorkWithPath should only ever return one result each.
    *
    * Setting up scroll context in Elasticsearch would appear to be an unnecessary
    * overhead for this process, However, the Akka Source/publisher way of loading
    * things requires a scroll query.
    *
    * Consistency is preferred here over pure performance, but a very short keepalive
    * is specified to release resources on ES as soon as possible.
    */
  private def queryPublisher(request: SearchRequest) =
    elasticClient.publisher(request.scroll(keepAlive = "1ms"))

  def getParentPath(path: String): Source[String, NotUsed] = {
    val request: SearchRequest = requestBuilder.parentPath(path)
    debug(
      s"Querying for parentPath with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(queryPublisher(request))
      .map(searchHit => searchHit.safeTo[PathHit].get.data.collectionPath.path)
  }

  def getWorkWithPath(path: String): Source[Work.Visible[Merged], NotUsed] = {
    val request: SearchRequest = requestBuilder.workWithPath(path)
    debug(
      s"Querying for work with path with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(queryPublisher(request))
      .map(searchHit => searchHit.safeTo[Work.Visible[Merged]].get)
  }

  def getChildWorks(path: String): Source[Work.Visible[Merged], NotUsed] = {
    val request: SearchRequest = requestBuilder.childWorks(path)
    debug(
      s"Querying for child works of path with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(queryPublisher(request))
      .map(searchHit => searchHit.safeTo[Work.Visible[Merged]].get)
  }

}
