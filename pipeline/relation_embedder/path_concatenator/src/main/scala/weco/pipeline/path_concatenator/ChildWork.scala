package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged

object ChildWork {

  /**
    * Return the childWork, having prepended the collectionPath from parentWork.
    * The collectionPath of a work contains its own path from its parent to itself,
    * so if its parent is not root, it needs to replace the parent node in its own path
    * with the parent node's path.
    *
    * e.g. grandparent/parent + parent/self -> grandparent/parent/self.
    *
    * This must be called with two Works with compatible collectionPath values.
    * The last node of the parent path must match the first node of the child path.
    *
    * Because of the iterative nature of this stage, it may be that, when this
    * is executed, the collectionPath on the child contains more than just parent/self,
    * and the "parent" is not the direct parent of self, but a further ancestor.
    * i.e. because it has already been executed once to resolve the grandparent/parent
    * part (and possibly again to add great-grandparent etc.)
    *
    * In that case it will still merge the paths as expected,
    *
    * e.g.
    * great-grandparent/grandparent + grandparent/parent/self
    * -> great-grandparent/grandparent/parent/self.
    *
    */
  def apply(parentWork: Work.Visible[Merged],
            childWork: Work.Visible[Merged]): Work.Visible[Merged] = {
    (parentWork.data.collectionPath, childWork.data.collectionPath) match {
      case (_, None) =>
        throw new IllegalArgumentException(
          s"Cannot prepend a parent path to '${childWork.state.canonicalId}', it does not have a collectionPath")
      case (None, _) =>
        throw new IllegalArgumentException(
          s"Cannot prepend the path from '${parentWork.state.canonicalId}', it does not have a collectionPath")
      case (Some(parentPath), Some(childPath)) =>
        val newChildPath = mergePaths(childPath, parentPath)
        // The path will be unchanged if parentPath is the root.
        // In this case, just return the childWork as-is
        if (newChildPath != childPath)
          withNewPath(childWork, newChildPath)
        else childWork
    }
  }

  private def mergePaths(childPath: CollectionPath,
                         parentPath: CollectionPath): CollectionPath = {
    val childRoot = firstNode(childPath.path)
    val parentLeaf = lastNode(parentPath.path)

    if (childRoot != parentLeaf) {
      throw new IllegalArgumentException(
        s"$parentPath is not the parent of $childRoot")
    } else {
      val fullPath =
        pathJoin(parentPath.path +: childPath.path.split("/").tail)
      childPath.copy(path = fullPath)
    }
  }

  private def withNewPath(work: Work.Visible[Merged],
                          newPath: CollectionPath): Work.Visible[Merged] =
    work.copy(data = work.data.copy(collectionPath = Some(newPath)))

  private def firstNode(path: String): String =
    path.splitAt(path.indexOf("/"))._1

  private def lastNode(path: String): String =
    path.splitAt(path.lastIndexOf("/") + 1)._2

  private def pathJoin(nodes: Seq[String]): String =
    nodes.mkString("/")
}
