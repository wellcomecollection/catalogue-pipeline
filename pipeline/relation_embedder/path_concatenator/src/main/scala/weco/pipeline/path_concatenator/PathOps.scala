package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.CollectionPath

object PathOps {
  implicit class StringOps(path: String) {

    lazy val firstNode: String =
      path.splitAt(path.indexOf("/"))._1

    lazy val lastNode: String =
      path.splitAt(path.lastIndexOf("/") + 1)._2
  }

  def concatenatePaths(parentPath: String,
                       childPath: CollectionPath): CollectionPath = {
    val childRoot = childPath.path.firstNode
    val parentLeaf = parentPath.lastNode

    if (childRoot != parentLeaf) {
      throw new IllegalArgumentException(
        s"$parentPath is not the parent of $childRoot")
    } else {
      childPath.copy(
        path = pathJoin(parentPath +: childPath.path.split("/").tail))
    }
  }
  def pathJoin(nodes: Seq[String]): String =
    nodes.mkString("/")
}
