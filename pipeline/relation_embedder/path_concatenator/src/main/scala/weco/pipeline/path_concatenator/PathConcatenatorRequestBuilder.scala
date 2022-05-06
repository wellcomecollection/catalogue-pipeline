package weco.pipeline.path_concatenator

import com.sksamuel.elastic4s.ElasticDsl.{
  constantScoreQuery,
  search,
  wildcardQuery
}
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.term.{TermQuery, WildcardQuery}

class PathConcatenatorRequestBuilder(index: Index) {
  import PathOps._

  /**
   * A query that returns the collectionPath from the parent Work (if any)
   * of the given path.
   *
   * The parent work is the one with a path whose leaf node matches
   * the root node of the given path.
   * e.g.
   * a/b/c is the parent of c/d/e
   *
   * There _should_, at most, be one record that matches this query,
   * and callers should act accordingly.
   *
   * However, because there is a possibility that errors in the data might
   * cause this query to yield more than one result, this request leaves
   * the size constraint set at the default (10), in order to allow the caller
   * to examine the number of results returned and take appropriate action.
   */
  def parentPath(path: String): SearchRequest =
    search(index)
      .query(constantScoreQuery(wildPathQuery(pathJoin(List("*", path.firstNode)))))
      .sourceInclude(("data.collectionPath.path"))

  def workWithPath(path:String): SearchRequest =
    search(index).query(constantScoreQuery(exactPathQuery(path)))

  def childWorks(path:String): SearchRequest =
    search(index)
      .query(constantScoreQuery(wildPathQuery(pathJoin(List(path.lastNode, "*")))))

  private def wildPathQuery(path: String): WildcardQuery =
    wildcardQuery(field = "data.collectionPath.path", value = path)

  private def exactPathQuery(path: String): TermQuery =
    termQuery(field = "data.collectionPath.path.keyword", value = path)
}
