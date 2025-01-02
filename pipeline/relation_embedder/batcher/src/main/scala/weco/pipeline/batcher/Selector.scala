package weco.pipeline.batcher

/** A `Selector` is used to match nodes within a particular archive. A selector
  * matches one of the following:
  *   - The `Tree` with some root `path`
  *   - A single `Node` at some `path`
  *   - The `Children` of some `path`
  *   - All `Descendents` of some `path`
  */
sealed trait Selector {

  val path: String

  lazy val rootPath: String =
    Selector.ancestors(path).headOption.getOrElse(path)

  /** Returns a list of all selectors which would also match this selector. We
    * use these to filter out any unnecessary selectors when another broader one
    * already exists.
    */
  private def superSelectors: List[Selector] = {
    import Selector._
    val ancestorPaths = ancestors(path)
    val ancestorDescendents = ancestorPaths.map(Descendents(_))
    val tree = Tree(rootPath)
    this match {
      case Tree(_) => Nil
      case Node(path) =>
        tree :: ancestorDescendents ++ parent(path).map(Children(_)).toList
      case Children(path) =>
        tree :: Descendents(path) :: ancestorDescendents
      case Descendents(_) =>
        tree :: ancestorDescendents
    }
  }

  def shouldSuppress(otherSelectors: Set[Selector]): Boolean =
    superSelectors.exists(otherSelectors.contains)
}

object Selector {

  private type SelectorPath = String

  case class Tree(path: SelectorPath) extends Selector

  case class Node(path: SelectorPath) extends Selector

  case class Children(path: SelectorPath) extends Selector

  case class Descendents(path: SelectorPath) extends Selector

  /** Given a list of input paths, return a list of selectors representing the
    * nodes which should be sent to the `relation_embedder` for denormalisation.
    * The output also includes the original path, as when sending a SNS
    * notification we need to map this back to the original input path in case
    * of failure.
    *
    * The generation of selectors uses the logic documented in `forPath`,
    * followed by filtering of any selectors which are already accounted for by
    * other broader selectors.
    *
    * For example, given the following tree:
    * {{{
    * A
    * |
    * |----------
    * | |       |
    * B C       E
    * | |----   |------
    * | | | |   | | | |
    * D X Y Z   1 2 3 4
    * }}}
    * We would not need to include selectors for `Node(X)` or `Children(Y)` if
    * for example either the selectors `Tree(A)` or `Descendents(C)` also
    * existed.
    */
  def forPaths(paths: Seq[Path]): List[(Selector, Path)] = {
    discardRedundantSelectors(mapSelectorsToPaths(paths)).toList
  }

  /** Given an input path, return a list of selectors representing the nodes
    * which should be sent to the `relation_embedder` for denormalisation.
    *
    * This consists of:
    *   - The nodes parent
    *   - All children of the nodes parent
    *   - All descendents of the node
    *
    * To see why this is the case, consider the following tree:
    *
    * {{{
    * A
    * |
    * |-------------
    * |  |         |
    * B  C         E
    * |  |------   |---------
    * |  |  |  |   |  |  |  |
    * D  X  Y  Z   1  2  3  4
    * }}}
    *
    * Given node `C` as an input, we need to denormalise the parent `A` as it
    * contains `C` as a child relation. We need to denormalise the input `C`
    * itself, plus all of its sibings due to them containing `C` as a sibling
    * relation, giving us the rule of all the parents children. Finally, we must
    * denormalise all descendents of `C` due to them containing `C` as an
    * ancestor relation.
    *
    * These rules generate the minimal set of nodes that need to be
    * denormalised. If instead for example we took the whole subtree from the
    * parent downwards this would result in the siblings descendents being
    * denormalised unnecessarily.
    */
  private def forPath(path: SelectorPath): List[Selector] =
    parent(path)
      .map {
        parent =>
          List(Node(parent), Children(parent), Descendents(path))
      }
      .getOrElse(List(Tree(path)))

  private def mapSelectorsToPaths(paths: Seq[Path]): Map[Selector, Path] =
    paths
      .flatMap(
        path => Selector.forPath(path.path).map(selector => (selector, path))
      )
      .groupBy(_._1)
      .map(_._2.head)

  private def discardRedundantSelectors(
    selectorMap: Map[Selector, Path]
  ): Map[Selector, Path] = {
    val selectorSet = selectorMap.keySet
    selectorMap.collect {
      case (selector, path) if !selector.shouldSuppress(selectorSet) =>
        (selector, path)
    }
  }

  private def parent(path: SelectorPath): Option[SelectorPath] =
    tokenize(path).dropRight(1) match {
      case Nil    => None
      case tokens => Some(join(tokens))
    }

  private def ancestors(path: SelectorPath): List[SelectorPath] = {
    val tokens = tokenize(path).dropRight(1)
    (1 to tokens.length).map {
      i =>
        join(tokens.slice(0, i))
    }.toList
  }

  private def tokenize(path: SelectorPath): List[String] =
    path.split("/").toList

  private def join(tokens: List[String]): String =
    tokens.mkString("/")
}
