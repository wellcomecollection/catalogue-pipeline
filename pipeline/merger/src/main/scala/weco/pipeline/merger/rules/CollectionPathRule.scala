package weco.pipeline.merger.rules

import cats.data.NonEmptyList
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.{CollectionPath, Work, WorkState}
import weco.pipeline.merger.logging.MergerLogging
import weco.pipeline.merger.models.FieldMergeResult

object CollectionPathRule extends FieldMergeRule with MergerLogging {
  import WorkPredicates._
  type FieldData = Option[CollectionPath]

  override def merge(
    target: Work.Visible[WorkState.Identified],
    sources: Seq[Work[WorkState.Identified]]
  ): FieldMergeResult[FieldData] =
    FieldMergeResult(
      data = getCollectionPath(target, sources),
      sources = getCalmCollectionPath.mergedSources(target, sources))

  def getCollectionPath(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]): Option[CollectionPath] =
    getCalmCollectionPath(target, sources).getOrElse(target.data.collectionPath)

  val getCalmCollectionPath: CollectionPathRule.PartialRule =
    new PartialRule {
      val isDefinedForTarget: WorkPredicate = teiWork
      val isDefinedForSource: WorkPredicate = singlePhysicalItemCalmWork

      def rule(target: Work.Visible[Identified],
               sources: NonEmptyList[Work[Identified]]): FieldData = {
        debug(s"Getting Calm collectionPath from ${describeWork(sources.head)}")
        sources.head.data.collectionPath
      }
    }
}
