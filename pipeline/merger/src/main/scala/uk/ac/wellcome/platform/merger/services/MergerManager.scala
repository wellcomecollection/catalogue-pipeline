package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.merger.models.MergerOutcome

class MergerManager(mergerRules: Merger) {

  /** Given a list of recorder work entries retrieved from VHS, and a
    * merging function, apply the function to these works.
    *
    * If we got an incomplete list of results from VHS (for example,
    * wrong versions), we skip the merge and return the original works.
    */
  def applyMerge(
    maybeWorks: Seq[Option[TransformedBaseWork]]): MergerOutcome = {
    val transformedBaseWorks = maybeWorks
      .collect {
        case Some(transformedBaseWork: TransformedBaseWork) =>
          transformedBaseWork
      }

    if (transformedBaseWorks.size == maybeWorks.size) {
      mergerRules.merge(transformedBaseWorks)
    } else {
      MergerOutcome(maybeWorks.flatten, Nil)
    }
  }
}
