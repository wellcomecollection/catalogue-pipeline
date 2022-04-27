package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged

object ChildWork {
  /**
   * Return the childWork, having prepended the collectionpath from parentWork.
   * The collectionPath of a work contains its own path from its parent to itself,
   * so if its parent is not root, it needs to replace the parent node in its own path
   * with the parent node's path.
   *
   * e.g. grandparent/parent + parent/self -> grandparent/parent/self.
   *
   * Because of the iterative nature of this stage, it may be that a collectionpath
   * contains more than just parent/self when this is executed.
   * i.e. because it has already been executed once to resolve the grandparent/parent
   * part (and possibly again to add great-grandparent etc.)
   *
   */
  def apply(parentWork: Work.Visible[Merged],
            childWork: Work.Visible[Merged]): Work.Visible[Merged] = {
    (parentWork.data.collectionPath, childWork.data.collectionPath) match {
      case (Some(parentPath), Some(childPath)) =>
        val newChildPath = mergePaths(childPath, parentPath)
        if (newChildPath != childPath)
          childWork.copy(
            data = childWork.data.copy(collectionPath = Some(newChildPath)))
        else childWork
      case (_, None) =>
        throw new IllegalArgumentException(
          s"Cannot prepend a parent path to '${childWork.state.canonicalId}', it does not have a collectionPath")
      case (None, _) =>
        throw new IllegalArgumentException(
          s"Cannot prepend the path from '${parentWork.state.canonicalId}', it does not have a collectionPath")
    }
  }
  private def firstNode(path: String): String =
    path.splitAt(path.indexOf("/"))._1

  private def lastNode(path: String): String =
    path.splitAt(path.lastIndexOf("/") + 1)._2

  private def mergePaths(childPath: CollectionPath,
                         parentPath: CollectionPath): CollectionPath = {
    val childRoot = firstNode(childPath.path)
    val parentLeaf = lastNode(parentPath.path)

    if (childRoot != parentLeaf) {
      throw new IllegalArgumentException(
        s"$parentPath is not the parent of $childRoot")
    } else {
      val fullPath =
        (parentPath.path +: childPath.path.split("/").tail).mkString("/")
      childPath.copy(path = fullPath)
    }
  }
}
