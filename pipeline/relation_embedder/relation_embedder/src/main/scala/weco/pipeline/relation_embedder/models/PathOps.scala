package weco.pipeline.relation_embedder.models

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
  }
}
