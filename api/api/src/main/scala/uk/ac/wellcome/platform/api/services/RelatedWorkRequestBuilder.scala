package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.{
  MultiSearchRequest,
  SearchRequest
}
import com.sksamuel.elastic4s.requests.searches.queries.Query

case class RelatedWorkRequestBuilder(index: Index,
                                     path: String,
                                     maxRelatedWorks: Int = 1000) {

  // To reduce response size and improve Elasticsearch performance we only
  // return core fields for works in the tree.
  val fieldWhitelist = List(
    "type",
    "version",
    "state.canonicalId",
    "state.sourceIdentifier.identifierType.id",
    "state.sourceIdentifier.identifierType.label",
    "state.sourceIdentifier.value",
    "state.sourceIdentifier.ontologyType",
    "data.title",
    "data.alternativeTitles",
    "data.collectionPath.path",
    "data.collectionPath.level.type",
    "data.collectionPath.label",
  )

  lazy val request: MultiSearchRequest =
    multi(
      childrenRequest,
      siblingsRequest,
      ancestorsRequest
    )

  /**
    * Query all direct children of the node with the given path.
    */
  lazy val childrenRequest: SearchRequest =
    relatedWorksRequest(
      pathQuery(path, depth + 1)
    )

  /**
    * Query all siblings of the node with the given path.
    */
  lazy val siblingsRequest: SearchRequest =
    relatedWorksRequest(
      ancestors.lastOption match {
        case Some(parent) =>
          must(
            pathQuery(parent, depth),
            not(termQuery(field = "data.collectionPath.path", value = path))
          )
        case None => matchNoneQuery()
      }
    )

  /**
    * Query all ancestors of the node with the given path.
    */
  lazy val ancestorsRequest: SearchRequest =
    relatedWorksRequest(
      ancestors match {
        case Nil => matchNoneQuery()
        case ancestors =>
          should(
            ancestors.map(ancestor => pathQuery(ancestor, pathDepth(ancestor)))
          )
      }
    )

  lazy val ancestors: List[String] =
    pathAncestors(path)

  lazy val depth: Int =
    ancestors.length + 1

  def pathQuery(path: String, depth: Int) =
    must(
      termQuery(field = "data.collectionPath.path", value = path),
      termQuery(field = "data.collectionPath.depth", value = depth),
    )

  def relatedWorksRequest(query: Query): SearchRequest =
    search(index)
      .query(query)
      .from(0)
      .limit(maxRelatedWorks)
      .sourceInclude(fieldWhitelist)

  def pathAncestors(path: String): List[String] =
    tokenize(path) match {
      case head :+ tail :+ _ =>
        val ancestor = join(head :+ tail)
        pathAncestors(ancestor) :+ ancestor
      case _ => Nil
    }

  def pathDepth(path: String): Int =
    tokenize(path).length

  def tokenize(path: String): List[String] =
    path.split("/").toList

  def join(tokens: List[String]): String =
    tokens.mkString("/")
}
