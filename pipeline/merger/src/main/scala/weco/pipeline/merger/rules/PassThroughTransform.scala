package weco.pipeline.merger.rules

import weco.catalogue.internal_model.work.InvisibilityReason.TeiWorksAreNotVisible
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

sealed trait PassThroughTransform {
  def apply(works: Seq[Work[Identified]]): Seq[Work[Identified]]
}

object DefaultPassThroughTransform extends PassThroughTransform {
  override def apply(works: Seq[Work[Identified]]): Seq[Work[Identified]] =
    works.map {
      // In the default pipeline behaviour we want tei works to be invisible
      // for the time being, so we change them here
      case work if WorkPredicates.teiWork(work) =>
        Work.Invisible[Identified](
          version = work.version,
          data = work.data,
          state = work.state,
          invisibilityReasons = List(TeiWorksAreNotVisible)
        )
      case work => work
    }
}

object TeiPassThroughTransform extends PassThroughTransform {
  override def apply(works: Seq[Work[Identified]]): Seq[Work[Identified]] =
    works
}
