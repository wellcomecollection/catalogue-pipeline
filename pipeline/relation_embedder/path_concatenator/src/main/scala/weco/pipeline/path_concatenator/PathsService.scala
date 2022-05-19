package weco.pipeline.path_concatenator

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.json.JsonUtil.exportDecoder

import scala.concurrent.{ExecutionContext, Future}

/**
 * Data classes used to store only the relevant data from parent path queries.
 */
case class ParentPathData(collectionPath: CollectionPath);
case class PathHit(
  data: ParentPathData
)

class PathsService(elasticClient: ElasticClient, index: Index)(
  implicit ec: ExecutionContext)
    extends Logging {
  private val requestBuilder = new PathConcatenatorRequestBuilder(index)

  def getParentPath(path: String): Future[Option[String]] = {
    val request: SearchRequest = requestBuilder.parentPath(path)
    elasticClient.execute(request).map { response =>
      {
        val searchResponse = response.result
        searchResponse.totalHits match {
          case 0 => None
          case 1 =>
            Some(
              searchResponse.hits.hits(0).to[PathHit].data.collectionPath.path)
          case n =>
            throw new RuntimeException(
              s"$path has $n possible parents, including ${searchResponse.ids}, at most one is expected")
        }
      }
    }
  }

  def getWorkWithPath(path: String): Future[Work.Visible[Merged]] = {
    val request: SearchRequest = requestBuilder.workWithPath(path)
    elasticClient.execute(request).map { response =>
      {
        val searchResponse = response.result
        searchResponse.totalHits match {
          case 0 =>
            throw new RuntimeException(s"No work found with path: $path")
          case 1 => searchResponse.hits.hits(0).to[Work.Visible[Merged]]
          case n =>
            throw new RuntimeException(
              s"$path corresponds to $n possible works, including ${searchResponse.ids}, exactly one is expected")
        }
      }
    }
  }

  def getChildWorks(path: String): Future[Seq[Work.Visible[Merged]]] = {
    val request: SearchRequest = requestBuilder.childWorks(path)
    debug(
      s"Querying for child works of path with ES request: ${elasticClient.show(request)}")
    elasticClient.execute(request).map { response =>
      {
        if(response.result.totalHits > requestBuilder.maxResponseSize)
          warn(msg=s"getChildWorks matched ${response.result.totalHits} works, which is more than the maximum response size ${requestBuilder.maxResponseSize}. Some works will not have their paths correctly updated")
        response.result.to[Work.Visible[Merged]]
      }
    }
  }

}
