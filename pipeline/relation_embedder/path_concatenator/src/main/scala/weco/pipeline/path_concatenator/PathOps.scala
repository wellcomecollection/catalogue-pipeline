package weco.pipeline.path_concatenator

object PathOps {
  implicit class StringOps(path: String) {

    lazy val firstNode: String =
      path.splitAt(path.indexOf("/"))._1

    lazy val lastNode: String =
      path.splitAt(path.lastIndexOf("/") + 1)._2
  }
  def concatenatePaths(parentPath: String, childPath: String): String = {
    val childRoot = childPath.firstNode
    val parentLeaf = parentPath.lastNode

    if (childRoot != parentLeaf) {
      throw new IllegalArgumentException(
        s"$parentPath is not the parent of $childPath"
      )
    } else {
      pathJoin(parentPath +: childPath.split("/").tail)
    }
  }

  def pathJoin(nodes: Seq[String]): String =
    nodes.mkString("/")
}
