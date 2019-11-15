package uk.ac.wellcome.platform.merger.services

import uk.ac.wellcome.models.work.internal.{BaseWork, TransformedBaseWork}

class MergerManager(mergerRules: Merger) {

  /** Given a list of recorder work entries retrieved from VHS, and a
    * merging function, apply the function to these works.
    *
    * If we got an incomplete list of results from VHS (for example,
    * wrong versions), we skip the merge and return the original works.
    */
  def applyMerge(
    maybeWorks: Seq[Option[TransformedBaseWork]]): Seq[BaseWork] = {
    val transformedBaseWorks = maybeWorks
      .collect {
        case Some(transformedBaseWork: TransformedBaseWork) =>
          transformedBaseWork
      }

    if (transformedBaseWorks.size == maybeWorks.size) {
      mergerRules.merge(transformedBaseWorks)
    } else {
      maybeWorks.flatten
    }
  }
}
