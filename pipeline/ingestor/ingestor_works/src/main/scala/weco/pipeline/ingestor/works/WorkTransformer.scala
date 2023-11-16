package weco.pipeline.ingestor.works

import io.circe.syntax._
import weco.catalogue.display_model.work.DisplayWork
import weco.catalogue.internal_model.work.WorkState
import weco.catalogue.internal_model.work.Work
import weco.pipeline.ingestor.works.models.{
  DebugInformation,
  IndexedWork,
  SourceWorkDebugInformation,
  WorkAggregatableValues,
  WorkFilterableValues
}
import weco.catalogue.display_model.Implicits._
import weco.pipeline.ingestor.common.models.WorkQueryableValues

import java.time.Instant

trait WorkTransformer {
  val deriveData: Work[WorkState.Denormalised] => IndexedWork =
    work => {
      val mergedTime = work.state.mergedTime
      val indexedTime = getIndexedTime

      val source = SourceWorkDebugInformation(
        id = work.state.canonicalId,
        identifier = work.state.sourceIdentifier,
        version = work.version,
        modifiedTime = work.state.sourceModifiedTime
      )

      work match {
        case visibleWork @ Work.Visible(_, _, _, redirectSources) => {
          val display = DisplayWork(visibleWork).asJson.deepDropNullValues

          IndexedWork.Visible(
            debug = DebugInformation.Visible(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
              redirectSources = redirectSources
            ),
            display = display,
            query = WorkQueryableValues(visibleWork),
            filterableValues = WorkFilterableValues(visibleWork),
            aggregatableValues = WorkAggregatableValues(visibleWork)
          )
        }

        case Work.Invisible(_, _, _, invisibilityReasons) =>
          IndexedWork.Invisible(
            debug = DebugInformation.Invisible(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
              invisibilityReasons = invisibilityReasons
            )
          )

        case Work.Redirected(_, redirectTarget, _) =>
          IndexedWork.Redirected(
            debug = DebugInformation.Redirected(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime
            ),
            redirectTarget = redirectTarget
          )

        case Work.Deleted(_, _, deletedReason) =>
          IndexedWork.Deleted(
            debug = DebugInformation.Deleted(
              source = source,
              mergedTime = mergedTime,
              indexedTime = indexedTime,
              deletedReason = deletedReason
            )
          )
      }
    }

  // This is a def rather than an inline call so we can override it in the
  // tests; in particular we want it to be deterministic when we're creating
  // example documents to send to the API repo.
  protected def getIndexedTime: Instant = Instant.now()
}

object WorkTransformer extends WorkTransformer
