package uk.ac.wellcome.platform.batcher

/** A `Selector` is used to match nodes within a particular archive. A selector
  * matches one of the following:
  * - A single `Item` at some `path`
  * - The `Children` of some `path`
  * - All `Descendents` of some `path`
  */
sealed trait Selector {

  val path: String

  /** Returns a list of all selectors which would also match this selector.
    * We use these to filter out any unnecessary selectors when another less
    * specific one already exists.
    */
  def superSelectors: List[Selector] = {
    import Selector._
    val ancestorDescendents = ancestors(path).map(Descendents(_))
    this match {
      case Item(path) =>
        parent(path).toList.map(Children(_)) ++ ancestorDescendents
      case Children(path) =>
        ancestorDescendents
      case Descendents(path) =>
        ancestorDescendents
    }
  }
}

object Selector {

  type Path = String

  /** Given an input path, return a list of selectors representing the nodes
    * which need to be sent to the `relation_embedder` for denormalisation.
    *
    * This consists of:
    * - The nodes parent
    * - All children of the nodes parent
    * - All descendents of the node
    *
    * To see why this is the case, consider the following tree:
    *
    * A
    * |
    * |-------------
    * |  |         |
    * B  C         E
    * |  |------   |---------
    * |  |  |  |   |  |  |  |
    * D  X  Y  Z   1  2  3  4
    *
    * Given node `C` as an input, we need to denormalise the parent `A` as it
    * contains `C` as a child. We need to denormalise the input `C`  itself,
    * plus all of its sibings due to them containing `C` as a sibling relation,
    * so that gives us the rule of all the parents children. Finally, we must
    * also denormalise all descendents of `C` due to them containing `C` as an
    * ancestor.
    *
    * These rules generate the minimal set of nodes that need to be
    * denormalised. If instead for example we took the whole subtree from the
    * parent downwards this would result in siblings descendents being
    * denormalised unnecessarily.
    */
  def forPath(path: Path): List[Selector] =
    parent(path)
      .map { parent =>
        List(Item(parent), Children(parent), Descendents(path))
      }
      .getOrElse(List(Item(path), Descendents(path)))

  case class Item(path: Path) extends Selector

  case class Children(path: Path) extends Selector

  case class Descendents(path: Path) extends Selector

  private def parent(path: Path): Option[Path] =
    tokenize(path).dropRight(1) match {
      case Nil    => None
      case tokens => Some(join(tokens))
    }

  private def ancestors(path: Path): List[Path] = {
    val tokens = tokenize(path).dropRight(1)
    (0 until tokens.length).map { i =>
      join(tokens.slice(0, i))
    }.toList
  }

  private def tokenize(path: Path): List[String] =
    path.split("/").toList

  private def join(tokens: List[String]): Path =
    tokens.mkString("/")
}
