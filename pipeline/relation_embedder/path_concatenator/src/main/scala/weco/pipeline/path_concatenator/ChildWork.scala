package weco.pipeline.path_concatenator

import weco.catalogue.internal_model.work.{Work, WorkState}

object ChildWork {
  import PathOps._

  /** Return the childWork, having prepended the collectionPath from parentWork. The collectionPath
    * of a work contains its own path from its parent to itself, so if its parent is not root, it
    * needs to replace the parent node in its own path with the parent node's path.
    *
    * e.g. grandparent/parent + parent/self -> grandparent/parent/self.
    *
    * This must be called with two Works with compatible collectionPath values. The last node of the
    * parent path must match the first node of the child path.
    *
    * Because of the iterative nature of this stage, it may be that, when this is executed, the
    * collectionPath on the child contains more than just parent/self, and the "parent" is not the
    * direct parent of self, but a further ancestor.
    * i.e. because it has already been executed once to resolve the grandparent/parent part (and
    * possibly again to add great-grandparent etc.)
    *
    * In that case it will still merge the paths as expected - e.g.
    *
    * great-grandparent/grandparent + grandparent/parent/self
    * -> great-grandparent/grandparent/parent/self.
    */
  def apply[State <: WorkState](
    parentPath: String,
    childWork: Work.Visible[State]
  ): Work.Visible[State] = {
    childWork.data.collectionPath match {
      case None =>
        throw new IllegalArgumentException(
          s"Cannot prepend a parent path to '${childWork.id}', it does not have a collectionPath"
        )
      case Some(childPath) =>
        val newChildPath =
          childPath.copy(concatenatePaths(parentPath, childPath.path))
        // The path will be unchanged if parentPath is the root.
        // In this case, just return the childWork as-is
        childWork.copy(
          data = childWork.data.copy(collectionPath = Some(newChildPath))
        )
    }
  }
}
