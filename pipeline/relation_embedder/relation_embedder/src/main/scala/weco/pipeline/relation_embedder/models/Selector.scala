package weco.pipeline.relation_embedder.models

sealed trait Selector {
  import weco.pipeline.relation_embedder.models.PathOps._

  val path: String

  lazy val depth: Int = path.depth + 1
}

object Selector {
  case class Tree(path: String) extends Selector

  case class Node(path: String) extends Selector

  case class Children(path: String) extends Selector

  case class Descendents(path: String) extends Selector
}
