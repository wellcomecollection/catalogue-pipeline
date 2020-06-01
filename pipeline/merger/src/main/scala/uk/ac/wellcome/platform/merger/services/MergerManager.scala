package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.merger.models.MergerOutcome

class MergerManager(mergerRules: Merger) {

  /** Given a list of recorder work entries retrieved from VHS, and a
    * merging function, apply the function to these works.
    *
    * If we got an incomplete list of results from VHS (for example,
    * wrong versions), we just merge which works are available.
    */
  def applyMerge(
    maybeWorks: Seq[Option[TransformedBaseWork]]): MergerOutcome = {
    mergerRules.merge(maybeWorks.flatten)
  }
}
