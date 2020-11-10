package uk.ac.wellcome.relation_embedder

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.Query

case class RelationsRequestBuilder(index: Index,
                                   path: String,
                                   scrollKeepAlive: String = "5m") {

  // To reduce response size and improve Elasticsearch performance we only
  // return core fields
  private val relationsFieldWhitelist = List(
    "version",
    "state.sourceIdentifier.identifierType.id",
    "state.sourceIdentifier.identifierType.label",
    "state.sourceIdentifier.value",
    "state.sourceIdentifier.ontologyType",
    "state.modifiedTime",
    "data.title",
    "data.collectionPath.path",
    "data.collectionPath.level.type",
    "data.collectionPath.label",
    "data.workType",
  )

  def allRelationsRequest(scrollSize: Int): SearchRequest =
    search(index)
      .query {
        must(
          termQuery(field = "data.collectionPath.path", value = collectionRoot),
          visibleQuery
        )
      }
      .from(0)
      .sourceInclude(relationsFieldWhitelist)
      .scroll(keepAlive = scrollKeepAlive)
      .size(scrollSize)

  def otherAffectedWorksRequest(scrollSize: Int): SearchRequest =
    search(index)
      .query {
        must(
          should(
            siblingsQuery,
            parentQuery,
            descendentsQuery
          ),
          visibleQuery
        )
      }
      .from(0)
      .scroll(keepAlive = scrollKeepAlive)
      .size(scrollSize)

  private val visibleQuery = termQuery(field = "type", value = "Visible")

  /**
    * Query all siblings of the node with the given path.
    */
  private lazy val siblingsQuery: Query =
    ancestors.lastOption match {
      case Some(parent) =>
        must(
          pathQuery(parent, depth),
          not(termQuery(field = "data.collectionPath.path", value = path))
        )
      case None => matchNoneQuery()
    }

  /**
    * Query the parent of the node with the given path.
    */
  private lazy val parentQuery: Query =
    ancestors.lastOption match {
      case None         => matchNoneQuery()
      case Some(parent) => pathQuery(parent, depth - 1)
    }

  /**
    * Query all descendents of the node with the given path.
    */
  private lazy val descendentsQuery: Query =
    must(
      termQuery(field = "data.collectionPath.path", value = path),
      not(termQuery(field = "data.collectionPath.depth", value = depth)),
    )

  private lazy val ancestors: List[String] =
    pathAncestors(path)

  private lazy val depth: Int =
    ancestors.length + 1

  private lazy val collectionRoot: String =
    ancestors.headOption.getOrElse(path)

  private def pathQuery(path: String, depth: Int) =
    must(
      termQuery(field = "data.collectionPath.path", value = path),
      termQuery(field = "data.collectionPath.depth", value = depth),
    )

  private def pathAncestors(path: String): List[String] =
    tokenize(path) match {
      case head :+ tail :+ _ =>
        val ancestor = join(head :+ tail)
        pathAncestors(ancestor) :+ ancestor
      case _ => Nil
    }

  private def tokenize(path: String): List[String] =
    path.split("/").toList

  private def join(tokens: List[String]): String =
    tokens.mkString("/")
}
