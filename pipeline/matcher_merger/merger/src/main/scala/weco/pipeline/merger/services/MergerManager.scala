package weco.pipeline.merger.services

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.models.MergerOutcome

class MergerManager(val mergerRules: Merger) {

  /** Given a list of recorder work entries retrieved from VHS, and a merging
    * function, apply the function to these works.
    *
    * If we got an incomplete list of results from VHS (for example, wrong
    * versions), we skip the merge and return the original works.
    */
  def applyMerge(maybeWorks: Seq[Option[Work[Identified]]]): MergerOutcome = {
    val works = maybeWorks.flatten
    if (works.size == maybeWorks.size) {
      val result = mergerRules.merge(works)
      // TEI works can have internal works, which the Merger adds to
      // resultWorks (as visible works from a live parent, or as deleted works
      // from a deleted parent), so the number of resulting works can be
      // greater than modifiedWorks.size
      assert(result.resultWorks.size >= works.size)
      result
    } else
      MergerOutcome.passThrough(works)
  }
}
