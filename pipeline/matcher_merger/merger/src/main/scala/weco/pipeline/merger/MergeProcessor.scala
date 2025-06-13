package weco.pipeline.merger

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.flows.FlowOps
import weco.json.JsonUtil.fromJson
import weco.lambda.Downstream
import weco.pipeline.matcher.models.MatcherResult
import weco.pipeline.merger.services.{IdentifiedWorkLookup, MergerManager, WorkRouter}
import weco.pipeline_storage.Indexer

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class MergerResponse(successes: Seq[String], failures: Seq[String])


// merge and index workOrImage
class MergeProcessor(
  sourceWorkLookup: IdentifiedWorkLookup,
  mergerManager: MergerManager,
  workOrImageIndexer: Indexer[Either[Work[Merged], Image[Initial]]],
  workRouter: WorkRouter,
  imageMsgSender: Downstream,
)(implicit val ec: ExecutionContext)
  extends Logging {
  import weco.pipeline_storage.Indexable._


  private type WorkSet = Seq[Option[Work[Identified]]]

  def process(messages: List[String]): Future[MergerResponse] = {

  }



  private def processMessage(
    message: String
  ): Future[List[WorkOrImage]] =
    for {
      matcherResult <- Future.fromTry(
        fromJson[MatcherResult](message)
      )

      workSets <- getWorkSets(matcherResult)
        .map(workSets => workSets.filter(_.flatten.nonEmpty))

      result = workSets match {
        case Nil => Nil
        case workSets =>
          workSets.flatMap(
            ws =>
              // We use the matcher result time as the "modified" time on
              // the merged works, because it reflects the last time the
              // matcher inspected the connections between these works.
              //
              // We *cannot* rely on the modified times of the individual
              // works -- this may cause us to drop updates if works
              // get unlinked.
              //
              // See https://github.com/wellcomecollection/docs/tree/8d83d75aba89ead23559584db2533e95ceb09200/rfcs/038-matcher-versioning
              applyMerge(ws, matcherResult.createdTime)
          )
      }
    } yield result

  private def getWorkSets(matcherResult: MatcherResult): Future[List[WorkSet]] =
    Future.sequence {
      matcherResult.works.toList.map {
        matchedIdentifiers =>
          sourceWorkLookup.fetchAllWorks(matchedIdentifiers.identifiers.toList)
      }
    }

  private def applyMerge(
    workSet: WorkSet,
    matcherResultTime: Instant
  ): Seq[WorkOrImage] =
    mergerManager
      .applyMerge(maybeWorks = workSet)
      .mergedWorksAndImagesWithTime(matcherResultTime)

//  private def sendWorkOrImage(workOrImage: WorkOrImage): Try[Unit] =
//    workOrImage match {
//      case Left(work)   => workRouter(work)
//      case Right(image) => imageMsgSender.notify(imageIndexable.id(image))
//    }
}
