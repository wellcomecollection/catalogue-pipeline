package uk.ac.wellcome.relation_embedder

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.Query

case class RelationsRequestBuilder(index: Index,
                                   path: String,
                                   maxRelatedWorks: Int = 2000) {

  // To reduce response size and improve Elasticsearch performance we only
  // return core fields
  val relationsFieldWhitelist = List(
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

  lazy val allRelationsRequest: SearchRequest =
    search(index)
      .query {
        termQuery(field = "data.collectionPath.path", value = collectionRoot)
      }
      .from(0)
      .limit(maxRelatedWorks)
      .sourceInclude(relationsFieldWhitelist)

  lazy val otherAffectedWorksRequest: SearchRequest =
    search(index)
      .query {
        should(
          siblingsQuery,
          parentQuery,
          descendentsQuery
        )
      }
      .from(0)
      .limit(maxRelatedWorks)

  /**
    * Query all siblings of the node with the given path.
    */
  lazy val siblingsQuery: Query =
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
  lazy val parentQuery: Query =
    ancestors.lastOption match {
      case None         => matchNoneQuery()
      case Some(parent) => pathQuery(parent, depth - 1)
    }

  /**
    * Query all descendents of the node with the given path.
    */
  lazy val descendentsQuery: Query =
    must(
      termQuery(field = "data.collectionPath.path", value = path),
      not(termQuery(field = "data.collectionPath.depth", value = depth)),
    )

  lazy val ancestors: List[String] =
    pathAncestors(path)

  lazy val depth: Int =
    ancestors.length + 1

  lazy val collectionRoot: String =
    ancestors.headOption.getOrElse(path)

  def pathQuery(path: String, depth: Int) =
    must(
      termQuery(field = "data.collectionPath.path", value = path),
      termQuery(field = "data.collectionPath.depth", value = depth),
    )

  def relatedWorksRequest(query: Query): SearchRequest =
    search(index)
      .query {
        must(query, termQuery(field = "type", value = "Visible"))
      }
      .from(0)
      .limit(maxRelatedWorks)
      .sourceInclude(relationsFieldWhitelist)

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
