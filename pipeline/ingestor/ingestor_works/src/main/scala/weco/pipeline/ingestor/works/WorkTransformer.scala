package weco.pipeline.ingestor.works

import io.circe.syntax._
import weco.catalogue.display_model.work.DisplayWork
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.catalogue.internal_model.work.Work
import weco.pipeline.ingestor.works.models.{DebugInformation, IndexedWork, SourceWorkDebugInformation}
import weco.catalogue.display_model.Implicits._

import java.time.Instant

trait WorkTransformer {
  val deriveData: Work[Denormalised] => IndexedWork =
    work => {
      val indexedWork = work.transition[Indexed]()

      val mergedTime = work.state.mergedTime
      val indexedTime = indexedWork.state.indexedTime

      val source = SourceWorkDebugInformation(
        identifier = work.state.sourceIdentifier,
        version = work.version,
        modifiedTime = work.state.sourceModifiedTime
      )

      val state = indexedWork.state

      indexedWork match {
        case w @ Work.Visible(_, data, _, redirectSources) =>
          IndexedWork.Visible(
            debug = DebugInformation.Visible(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
              redirectSources = redirectSources
            ),
            state = state,
            data = data,
            display = DisplayWork(w).asJson.deepDropNullValues
          )

        case Work.Invisible(_, data, _, invisibilityReasons) =>
          IndexedWork.Invisible(
            debug = DebugInformation.Invisible(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
              invisibilityReasons = invisibilityReasons
            ),
            state = state,
            data = data
          )

        case Work.Redirected(_, redirectTarget, _) =>
          IndexedWork.Redirected(
            debug = DebugInformation.Redirected(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
            ),
            state = state,
            redirectTarget = redirectTarget
          )

        case Work.Deleted(_, _, deletedReason) =>
          IndexedWork.Deleted(
            debug = DebugInformation.Deleted(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
              deletedReason = deletedReason
            ),
            state = state
          )
      }
    }

  // This is a def rather than an inline call so we can override it in the
  // tests; in particular we want it to be deterministic when we're creating
  // example documents to send to the API repo.
  protected def getIndexedTime: Instant = Instant.now()
}

object WorkTransformer extends WorkTransformer
