package weco.pipeline.relation_embedder

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.Query
import weco.pipeline.relation_embedder.models._

case class RelationsRequestBuilder(index: Index,
                                   scrollKeepAlive: String = "2m") {

  // To reduce response size and improve Elasticsearch performance we only
  // return core fields
  private val relationsFieldWhitelist = List(
    "state.canonicalId",
    "state.availabilities",
    "data.title",
    "data.collectionPath.path",
    "data.collectionPath.label",
    "data.workType",
  )

  def completeTree(batch: Batch, scrollSize: Int): SearchRequest =
    search(index)
      .query(must(visibleQuery, pathQuery(batch.rootPath)))
      .from(0)
      .sourceInclude(relationsFieldWhitelist)
      .scroll(keepAlive = scrollKeepAlive)
      .size(scrollSize)

  // While the affected works do exist in the `completeTree`, we need
  // to make another request for them to get the _full_ work documents
  // which can then be denormalised.
  def affectedWorks(batch: Batch, scrollSize: Int): SearchRequest =
    search(index)
      .query(should(batch.selectors.map(_.query)))
      .from(0)
      .scroll(keepAlive = scrollKeepAlive)
      .size(scrollSize)

  private val visibleQuery = termQuery(field = "type", value = "Visible")

  private def pathQuery(path: String) =
    termQuery(field = "data.collectionPath.path", value = path)

  private def depthQuery(depth: Int) =
    termQuery(field = "data.collectionPath.depth", value = depth)

  implicit private class SelectorToQuery(selector: Selector) {
    import Selector._
    def query: Query =
      selector match {
        case Tree(path) =>
          pathQuery(path)
        case Node(path) =>
          must(pathQuery(path), depthQuery(selector.depth))
        case Children(path) =>
          must(pathQuery(path), depthQuery(selector.depth + 1))
        case Descendents(path) =>
          must(pathQuery(path), not(depthQuery(selector.depth)))
      }
  }
}
