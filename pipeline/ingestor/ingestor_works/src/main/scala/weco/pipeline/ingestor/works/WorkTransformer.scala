package weco.pipeline.ingestor.works

import weco.catalogue.display_model.work.DisplayWork
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.catalogue.internal_model.work.Work
import weco.pipeline.ingestor.works.models.{
  DebugInformation,
  IndexedWork,
  SourceWorkDebugInformation
}

object WorkTransformer {
  val deriveData: Work[Denormalised] => IndexedWork =
    work => {
      val indexedWork = work.transition[Indexed]()

      val source = SourceWorkDebugInformation(
        identifier = indexedWork.state.sourceIdentifier,
        version = indexedWork.version,
        modifiedTime = indexedWork.state.sourceModifiedTime
      )

      indexedWork match {
        case w @ Work.Visible(_, data, state, redirectSources) =>
          IndexedWork.Visible(
            debug = DebugInformation.Visible(
              source = source,
              mergedTime = indexedWork.state.mergedTime,
              indexedTime = indexedWork.state.indexedTime,
              redirectSources = redirectSources
            ),
            state = state,
            data = data,
            display = DisplayWork(w)
          )

        case Work.Invisible(_, data, state, invisibilityReasons) =>
          IndexedWork.Invisible(
            debug = DebugInformation.Invisible(
              source = source,
              mergedTime = indexedWork.state.mergedTime,
              indexedTime = indexedWork.state.indexedTime,
              invisibilityReasons = invisibilityReasons
            ),
            state = state,
            data = data
          )

        case Work.Redirected(_, redirectTarget, state) =>
          IndexedWork.Redirected(
            debug = DebugInformation.Redirected(
              source = source,
              mergedTime = indexedWork.state.mergedTime,
              indexedTime = indexedWork.state.indexedTime,
            ),
            state = state,
            redirectTarget = redirectTarget
          )

        case Work.Deleted(_, state, deletedReason) =>
          IndexedWork.Deleted(
            debug = DebugInformation.Deleted(
              source = source,
              mergedTime = indexedWork.state.mergedTime,
              indexedTime = indexedWork.state.indexedTime,
              deletedReason = deletedReason
            ),
            state = state
          )
      }
    }
}
