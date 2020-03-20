package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.SearchRequest

/**
  * Builds an elasticsearch request for fetching nodes in some Collection tree.
  */
case class CollectionRequestBuilder(index: Index,
                                    expandedPaths: List[String],
                                    maxNodes: Int = 1000) {

  val excludeFields = List(
    "data.items",
    "data.notes",
    "data.production",
    "data.subjects",
    "data.genres",
    "data.images",
  )

  lazy val request: SearchRequest =
    search(index)
      .query(query)
      .from(0)
      .limit(maxNodes)
      .sourceExclude(excludeFields)

  lazy val query: Query =
    should(expandedPaths.map(expandPathQuery))

  /**
    * Get the node at some path, it's direct children, it's ancestors, and the
    * direct children of those ancestors.
    */
  def expandPathQuery(path: String): Query = {
    val ancestors = pathAncestors(path)
    val root = ancestors.headOption.getOrElse(path)
    should(
      getNodeQuery(root) ::
        (path :: ancestors).map(getChildrenQuery))
  }

  /**
    * Get the node with the given path.
    */
  def getNodeQuery(path: String): Query =
    must(
      termQuery(field = "data.collection.depth", value = tokenize(path).length),
      termQuery(field = "data.collection.path", value = path)
    )

  /**
    * Get all direct children of the node with the given path.
    */
  def getChildrenQuery(path: String): Query =
    must(
      termQuery(
        field = "data.collection.depth",
        value = tokenize(path).length + 1),
      termQuery(field = "data.collection.path", value = path)
    )

  def pathAncestors(path: String): List[String] =
    tokenize(path) match {
      case head :+ tail :+ _ =>
        val ancestor = join(head :+ tail)
        pathAncestors(ancestor) :+ ancestor
      case _ => Nil
    }

  def tokenize(path: String): List[String] =
    path.split("/").toList

  def join(tokens: List[String]): String =
    tokens.mkString("/")
}
