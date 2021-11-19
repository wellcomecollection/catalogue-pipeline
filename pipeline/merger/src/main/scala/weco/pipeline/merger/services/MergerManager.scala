package weco.pipeline.merger.services

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.models.MergerOutcome

class MergerManager(val mergerRules: Merger) {

  /** Given a list of recorder work entries retrieved from VHS, and a
    * merging function, apply the function to these works.
    *
    * If we got an incomplete list of results from VHS (for example,
    * wrong versions), we skip the merge and return the original works.
    */
  def applyMerge(maybeWorks: Seq[Option[Work[Identified]]]): MergerOutcome = {
    val works = maybeWorks.flatten
    if (works.size == maybeWorks.size) {
      val result = mergerRules.merge(works)
      // TEI works can have internal works, which are added to
      // resultWorks by the Merger, so the number of resulting works can be
      // greater than modifiedWorks.size
      assert(result.resultWorks.size >= works.size)
      result
    } else
      MergerOutcome.passThrough(works)
  }
}
