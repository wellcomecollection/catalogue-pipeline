package weco.catalogue.transformer

import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source

object WorkComparison {

  implicit class SourceWorkOps(proposedUpdate: Work[Source]) {

    /** This function decides if a work should replace an existing, stored work.
      *
      * A work should be replaced if:
      *
      *    - A version that is less than or equal to the suggested update, and
      *    - The data in the suggested update is different to the stored data
      *
      * An update can be safely ignored if:
      *
      *    - The version is strictly less than the stored work (i.e. the update
      *      is older than the stored work)
      *    - It contains the same data as the stored work (i.e. there's no difference,
      *      so applying this update would be a no-op.
      *
      */
    def shouldReplace(storedWork: Work[Source]): Boolean = {
      assert(
        storedWork.id == proposedUpdate.id,
        s"Cannot compare two works with different IDs: ${storedWork.id} != ${proposedUpdate.id}"
      )

      val isNewer = proposedUpdate.version >= storedWork.version
      val isDifferent = proposedUpdate.data != storedWork.data

      isNewer && isDifferent
    }
  }
}
