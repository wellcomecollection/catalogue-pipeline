package weco.catalogue.transformer

import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source

object WorkComparison {

  /** Returns true if `work` is:
    *
    *    - newer than `storedWork`
    *    - meaningfully different to `storedWork`
    *
    */
  def isNewer(storedWork: Work[Source], work: Work[Source]): Boolean = {
    assert(
      storedWork.id == work.id,
      s"Cannot compare two works with different IDs: ${storedWork.id} != ${work.id}"
    )

    // If the stored work has a higher version, then it automatically wins.
    if (storedWork.version > work.version) {
      false
    }
    // If the stored work has a lower or equal version, but the same data,
    // then it is the same.
    else {
      storedWork.data != work.data
    }
  }
}
