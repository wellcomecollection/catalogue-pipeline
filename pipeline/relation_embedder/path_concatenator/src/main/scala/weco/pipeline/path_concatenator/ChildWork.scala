package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.{CollectionPath, Work, WorkState}

object ChildWork {
  import PathOps._

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
    * In that case it will still merge the paths as expected - e.g.
    *
    * great-grandparent/grandparent + grandparent/parent/self
    * -> great-grandparent/grandparent/parent/self.
    *
    */
  def apply[State <: WorkState](parentPath: String,
            childWork: Work.Visible[State]): Work.Visible[State] = {
    childWork.data.collectionPath match {
      case None =>
        throw new IllegalArgumentException(
          s"Cannot prepend a parent path to '${childWork}', it does not have a collectionPath")
      case Some(childPath) =>
        val newChildPath = mergePaths(parentPath, childPath)
        // The path will be unchanged if parentPath is the root.
        // In this case, just return the childWork as-is
        if (newChildPath != childPath)
          withNewPath(childWork, newChildPath)
        else childWork
    }
  }

  private def mergePaths(parentPath: String,
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

  private def withNewPath[State<: WorkState](work: Work.Visible[State],
                          newPath: CollectionPath): Work.Visible[State] =
    work.copy(data = work.data.copy(collectionPath = Some(newPath)))

}
