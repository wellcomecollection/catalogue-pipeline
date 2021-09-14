package weco.pipeline.merger.services

import weco.catalogue.internal_model.work.{DeletedReason, Work}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.pipeline.merger.models.MergerOutcome
import weco.pipeline.merger.rules.WorkPredicates

trait MergerManager {
  val mergerRules: Merger

  /** Given a list of recorder work entries retrieved from VHS, and a
    * merging function, apply the function to these works.
    *
    * If we got an incomplete list of results from VHS (for example,
    * wrong versions), we skip the merge and return the original works.
    */
  def applyMerge(maybeWorks: Seq[Option[Work[Identified]]]): MergerOutcome = {
    val works = maybeWorks.flatten
    val modifiedWorks = preMergeModify(works)
    if (works.size == maybeWorks.size) {
      val result = mergerRules.merge(modifiedWorks)
      // TEI works can have internal works, which are added to
      // resultWorks by the Merger, so the number of resulting works can be
      // greater than modifiedWorks.size
      assert(result.resultWorks.size >= modifiedWorks.size)
      result
    } else
      MergerOutcome.passThrough(modifiedWorks)
  }

  protected def preMergeModify(
    works: Seq[Work[Identified]]): Seq[Work[Identified]]
}

class TeiOffMergerManager(val mergerRules: Merger) extends MergerManager {
  override protected def preMergeModify(
    works: Seq[Work[Identified]]): Seq[Work[Identified]] =
    works.map(
      work =>
        // In the default pipeline behaviour we want tei works to be filtered out
        // until we're happy with the merging work.
        if (WorkPredicates.teiWork(work)) {
          Work.Deleted[Identified](
            version = work.version,
            state = work.state,
            deletedReason = DeletedReason.TeiDeletedInMerger)
        } else {
          work
      })
}
class TeiOnMergerManager(val mergerRules: Merger) extends MergerManager {

  override protected def preMergeModify(
    works: Seq[Work[Identified]]): Seq[Work[Identified]] = works
}

object MergerManager {
  def teiOnMergerManager = new TeiOnMergerManager(PlatformMerger)
  def teiOffMergerManager = new TeiOffMergerManager(PlatformMerger)
}
