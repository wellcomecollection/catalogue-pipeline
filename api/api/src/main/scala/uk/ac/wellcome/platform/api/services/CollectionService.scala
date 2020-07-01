package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchHit

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.models.work.internal.Collection
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.platform.api.Tracing

/**
  * Retrieves a Collection given some paths to expand.
  *
  * For example, if asked to expand the path "A/B", it will return a tree
  * consisting of the node A, the child node B, and any children of either of
  * those nodes.
  *
  * A
  * ├── B
  * │   ├── ?
  * │   ├── ?
  * │   └── ?
  * │
  * ├── ?
  * └── ?
  *
  * More specifically, it will return the requested node, any direct children it
  * has, all ancestors it has, and any direct children of those ancestors.
  *
  * Note that this tree might not contain the complete tree data for some
  * hierarchical collection that exists in the index: it is intended to allow
  * incremental exploration of the data (akin to exploring a filesystem), due to
  * the fact the whole tree may be rather large.
  */
class CollectionService(elasticsearchService: ElasticsearchService)(
  implicit ec: ExecutionContext)
    extends Tracing {

  def retrieveTree(index: Index,
                   expandedPaths: List[String]): Future[Result[Collection]] =
    elasticsearchService
      .executeSearchRequest(
        CollectionRequestBuilder(index, expandedPaths).request
      )
      .map { result =>
        result.left
          .map(_.asException)
          .flatMap { searchResponse =>
            searchResponse.hits.hits.toList.map(toWork).toResult
          }
          .flatMap(Collection(_))
      }

  // Note that the search request should only return work with type
  // IdentifiedWork due to the fact that we are filtering on data.collection
  private def toWork(hit: SearchHit): Result[IdentifiedWork] =
    fromJson[IdentifiedWork](hit.sourceAsString).toEither
}
