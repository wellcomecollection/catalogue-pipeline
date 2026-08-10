package weco.pipeline.transformer

import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.Work
import weco.pipeline.transformer.result.Result

trait Transformer[SourceData] {

  def apply(
    id: String,
    sourceData: SourceData,
    version: Int
  ): Result[Work[Source]]

  /** Carry over state that the new work cannot derive from the source record,
    * e.g. TEI internal work stubs on a deletion.
    */
  def reconcileWithStored(
    newWork: Work[Source],
    storedWork: Work[Source]
  ): Work[Source] = newWork

  /** Whether this work would be wrong without the stored work to reconcile
    * against. If it would, the transformer fails rather than sending it.
    */
  def requiresStoredWork(newWork: Work[Source]): Boolean = false
}
