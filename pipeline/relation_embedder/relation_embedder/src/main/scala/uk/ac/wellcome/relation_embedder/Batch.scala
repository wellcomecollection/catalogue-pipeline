package uk.ac.wellcome.relation_embedder

case class Batch(rootPath: String, selectors: List[Selector])

sealed trait Selector {

  val path: String

  lazy val depth: Int =
    path.split("/").length
}

object Selector {

  case class Tree(path: String) extends Selector

  case class Node(path: String) extends Selector

  case class Children(path: String) extends Selector

  case class Descendents(path: String) extends Selector
}
