package weco.pipeline.relation_embedder.models

object PathOps {
  implicit class StringOps(path: String) {

    /** Returns the parent of an archive path -- everything before the final
      * slash.
      *
      * e.g. the parent of PP/CRI/J/2/3 is PP/CRI/J/2
      */
    lazy val parent: String = {
      val parts = path.split("/").toList
      val parentParts = parts.dropRight(1)
      parentParts.mkString("/")
    }

    /** Returns the depth of an archive path -- how far it is below the top
      * level.
      *
      * e.g. the depth of PP/CRI/J/2/3 is 4
      */
    lazy val depth: Int =
      path.split("/").length - 1
  }
}
