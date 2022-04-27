package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged

object ChildWork {
  def apply(parentWork: Work.Visible[Merged], childWork:Work.Visible[Merged]): Work.Visible[Merged] = {
    (parentWork.data.collectionPath, childWork.data.collectionPath) match {
      case (Some(parentPath), Some(childPath)) =>
        val newChildPath = mergePaths(childPath, parentPath)
        if(newChildPath != childPath) childWork.copy(
          data = childWork.data.copy(collectionPath = Some(newChildPath)))
        else childWork
      case (_, None) =>
        throw new IllegalArgumentException(s"Cannot prepend a parent path to '${childWork.state.canonicalId}', it does not have a collectionPath")
      case (None, _) =>
        throw new IllegalArgumentException(s"Cannot prepend the path from '${parentWork.state.canonicalId}', it does not have a collectionPath")
    }
  }
  private def firstNode(path: String): String =
    path.splitAt(path.indexOf("/"))._1

  private def lastNode(path: String): String =
    path.splitAt(path.lastIndexOf("/") + 1)._2

  private def mergePaths(childPath: CollectionPath, parentPath: CollectionPath): CollectionPath = {
    val childRoot = firstNode(childPath.path)
    val parentLeaf = lastNode(parentPath.path)

    if(childRoot != parentLeaf) {
      throw new IllegalArgumentException(s"$parentPath is not the parent of $childRoot")
    } else {
      val fullPath = (parentPath.path +: childPath.path.split("/").tail).mkString("/")
      childPath.copy(path=fullPath)
    }
  }
}
