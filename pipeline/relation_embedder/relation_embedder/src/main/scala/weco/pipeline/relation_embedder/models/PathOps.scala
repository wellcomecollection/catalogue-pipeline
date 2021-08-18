package weco.pipeline.relation_embedder.models

import weco.pipeline.relation_embedder.CollectionPathSorter

object PathOps {
  implicit class StringOps(path: String) {

    /** Returns the parent of an archive path -- everything before the
      * final slash.
      *
      * e.g. the parent of PP/CRI/J/2/3 is PP/CRI/J/2
      *
      */
    def parent: String = {
      val parts = path.split("/").toList
      val parentParts = parts.dropRight(1)
      parentParts.mkString("/")
    }
  }

  implicit class CollectionOps(paths: Set[String]) {
    /** Returns a list of paths in ``works`` whose parents are also in the
     * list of works.
     *
     * e.g. if the works have paths
     *
     *      A/B
     *      A/B/1
     *      A/B/2
     *      A/B/2/1
     *      A/B/2/2
     *      A/B/3/1
     *
     * then this would return
     *
     *      Map(
     *        A/B/1 -> A/B,
     *        A/B/2 -> A/B,
     *        A/B/2/1 -> A/B/2,
     *        A/B/2/2 -> A/B/2
     *      )
     *
     * Notice that A/B and A/B/3/1 are missing, because their parents (A and A/B/3)
     * are not in the list of works.
     *
     */
    def parentMapping: Map[String, String] =
      paths
        .map { p => p -> p.parent }
        .filter { case (_, parentPath) => paths.contains(parentPath) }
        .toMap

    /** Returns a list of paths in ``works``, and a list of their immediate children.
     *
     * e.g. if the works have paths
     *
     *      A/B
     *      A/B/1
     *      A/B/2
     *      A/B/2/1
     *      A/B/2/2
     *      A/B/3/1
     *
     * then this would return
     *
     *      Map(
     *        A/B -> List(A/B/1, A/B/2),
     *        A/B/1 -> List(),
     *        A/B/2 -> List(A/B/2/1, A/B/2/2),
     *        A/B/2/1 -> List(),
     *        A/B/2/2 -> List(),
     *        A/B/3/1 -> List()
     *      )
     *
     * Notice that although all of these are below A/B, it only lists A/B/1 and
     * A/B/2 because those are the immediate children.
     *
     * The children are sorted in CollectionPath order.
     *
     */
    def childMapping: Map[String, List[String]] =
      paths
        .map { p =>
          val childPaths = parentMapping
            .collect {
              case (childPath, parentPath) if parentPath == p =>
                childPath
            }
            .toList

          require(childPaths.forall(_.parent == p))

          p -> CollectionPathSorter.sortPaths(childPaths)
        }
        .toMap

    /** Returns the siblings of ``path``.
     *
     * The result is two lists: the before/after siblings when arranged in order.
     *
     * e.g. if the works have paths
     *
     *   A/B
     *   A/B/1
     *   A/B/2
     *   A/B/3
     *   A/B/3/1
     *   A/B/4
     *   A/B/4/1
     *
     * then the siblings of A/B/3 would be (A/B/1, A/B/2) and (A/B/4,)
     */
    def siblingsOf(p: String): (List[String], List[String]) = {
      val siblings = for {
        // The children of your parents are your siblings
        parent <- parentMapping.get(p)
        childrenOfParent = childMapping(parent)

        // Where does this path fall in the list of children?
        index = childrenOfParent.indexOf(p)
        (preceding, succeedingAndSelf) = childrenOfParent.splitAt(index)

        // Remember to remove yourself from the list of children
        succeeding = succeedingAndSelf.tail
      } yield (preceding, succeeding)

      siblings.getOrElse((List(), List()))
    }

    /** Returns the children of ``path``.
      *
      * The result is a list, which may be empty if this path isn't in the set
      * or it doesn't have any children.
      */
    def childrenOf(p: String): List[String] =
      childMapping.getOrElse(p, List())
  }
}
