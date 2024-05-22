package weco.pipeline.path_concatenator

object PathOps {
  implicit class StringOps(path: String) {

    lazy val firstNode: String =
      path.splitAt(path.indexOf("/"))._1

    lazy val lastNode: String =
      path.splitAt(path.lastIndexOf("/") + 1)._2

    lazy val isCircular: Boolean =
      path.indexOf("/") != -1 && firstNode == lastNode

    lazy val isSimple: Boolean =
      path.indexOf("/") == -1
  }
  def concatenatePaths(parentPath: String, childPath: String): String = {
    val childRoot = childPath.firstNode
    val parentLeaf = parentPath.lastNode

    if (childRoot != parentLeaf) {
      throw new IllegalArgumentException(
        s"$parentPath is not the parent of $childPath"
      )
    } else {
      val pathFragments = parentPath.split("/") ++ childPath.split("/").tail
      val repeatedFragments = pathFragments.groupBy(identity).collect {
        case (fragment, occurrences) if occurrences.lengthCompare(1) != 0 =>
          fragment
      }

      repeatedFragments match {
        case Nil => pathJoin(pathFragments)
        case seq =>
          throw new IllegalArgumentException(
            s"${pathJoin(pathFragments)} contains circular section(s) at ${seq.mkString(", ")}"
          )
      }

    }
  }

  def pathJoin(nodes: Seq[String]): String =
    nodes.mkString("/")
}
