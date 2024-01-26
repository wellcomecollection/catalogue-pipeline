package weco.pipeline.merger.rules

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

object TargetPrecedence {
  import WorkPredicates._

  // This is the canonical list of the order in which we try to select target works
  private val targetPrecedence = Seq(
    teiWork,
    singlePhysicalItemCalmWork,
    sierraDigitisedAv,
    physicalSierra,
    sierraWork
  )

  def targetSatisfying(
    additionalPredicate: WorkPredicate
  )(works: Seq[Work[Identified]]): Option[Work.Visible[Identified]] =
    targetPrecedence.view
      .flatMap(pred => works.find(work => pred(work) && additionalPredicate(work)))
      .headOption
      .collect(visibleWork)

  def getTarget(
    works: Seq[Work[Identified]]
  ): Option[Work.Visible[Identified]] =
    targetSatisfying(anyWork)(works)

  def visibleWork: PartialFunction[Work[Identified], Work.Visible[Identified]] = {
    case work: Work.Visible[Identified] => work
  }
}
