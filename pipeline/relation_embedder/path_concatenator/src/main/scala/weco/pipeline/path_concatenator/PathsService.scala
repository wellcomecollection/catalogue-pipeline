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

  def getParentPath(path: String): Source[String, NotUsed] = {
    val request: SearchRequest = requestBuilder.parentPath(path)
    debug(
      s"Querying for parentPath with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request.scroll(keepAlive = "1ms")))
      .map(searchHit => searchHit.safeTo[PathHit].get.data.collectionPath.path)
  }

  def getWorkWithPath(path: String): Source[Work[Merged], NotUsed] = {
    val request: SearchRequest = requestBuilder.workWithPath(path)
    debug(
      s"Querying for work with path with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request.scroll(keepAlive = "1ms")))
      .map(searchHit => searchHit.safeTo[Work[Merged]].get)
  }

  def getChildWorks(path: String): Source[Work[Merged], NotUsed] = {
    val request: SearchRequest = requestBuilder.childWorks(path)
    debug(
      s"Querying for child works of path with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request.scroll(keepAlive = "1ms")))
      .map(searchHit => searchHit.safeTo[Work[Merged]].get)
  }

}
