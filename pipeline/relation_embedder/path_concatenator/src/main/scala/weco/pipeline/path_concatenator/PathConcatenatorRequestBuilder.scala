package weco.pipeline.path_concatenator

import com.sksamuel.elastic4s.ElasticApi.termQuery
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
    *
    * 10K is the largest response size ES normally allows from non-scroll queries
    * In reality, the largest possible response to a childWorks query
    * (given the current data) will be less than 1400.
    * - The Fallaize collection is the largest set of works that will use this facility
    * - There are, at time of writing, 1313 documents that match "Fallaize" in a free text search,
    * - Because of the order of processing, it is unlikely, but possible for all of them to be returned at once.
    *
    * Making a request with a larger size limit than required
    * should not cause any degradation in performance, so setting it to the ES default
    * index.max_result_window should be harmless and will ensure plenty of space for
    * the collection to grow.
    */
  val maxResponseSize: Int = 10000

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
      .query(
        constantScoreQuery(wildPathQuery(pathJoin(List("*", path.firstNode)))))
      .sourceInclude(("data.collectionPath.path"))

  /**
    * A query that returns the Work that has the exact path given in `path`
    */
  def workWithPath(path: String): SearchRequest =
    search(index).query(constantScoreQuery(exactPathQuery(path)))

  /**
    * A query that returns all works with a path that starts at the end of the given path.
    * e.g. given works in the index with paths b/c, b/d and b/e/f,
    * executing the childWorks("a/b") query will return all of those works.
    *
    * The query includes a large size setting. By default, Elasticsearch will only return 10 results
    * and it is likely that there will be more than 10 to return in any given run through the concatenator.
    */
  def childWorks(path: String): SearchRequest =
    search(index)
      .query(
        constantScoreQuery(wildPathQuery(pathJoin(List(path.lastNode, "*")))))
      .size(maxResponseSize)

  private def wildPathQuery(path: String): WildcardQuery =
    wildcardQuery(field = "data.collectionPath.path", value = path)

  private def exactPathQuery(path: String): TermQuery =
    termQuery(field = "data.collectionPath.path.keyword", value = path)
}
