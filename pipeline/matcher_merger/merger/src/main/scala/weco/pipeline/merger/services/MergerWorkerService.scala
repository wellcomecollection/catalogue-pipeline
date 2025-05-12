package weco.pipeline.merger.services

import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.{Done, NotUsed}
import software.amazon.awssdk.services.sqs.model.Message
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.flows.FlowOps
import weco.pipeline.matcher.models.MatcherResult._
import weco.json.JsonUtil.fromJson
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline.matcher.models.MatcherResult
import weco.pipeline_storage.{Indexer, PipelineStorageConfig}
import weco.typesafe.Runnable

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MergerWorkerService[WorkDestination, ImageDestination](
  msgStream: SQSStream[NotificationMessage],
  sourceWorkLookup: IdentifiedWorkLookup,
  mergerManager: MergerManager,
  workOrImageIndexer: Indexer[
    Either[Work[Merged], Image[Initial]]
  ],
  workRouter: WorkRouter[WorkDestination],
  imageMsgSender: MessageSender[ImageDestination],
  config: PipelineStorageConfig
)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps {

  import weco.pipeline_storage.Indexable._
  import weco.pipeline_storage.PipelineStorageStream._

  type WorkOrImage = Either[Work[Merged], Image[Initial]]

  type WorkSet = Seq[Option[Work[Identified]]]

  def run(): Future[Done] =
    for {
      _ <- workOrImageIndexer.init()
      _ <- msgStream.runStream(
        this.getClass.getSimpleName,
        source =>
          source
            .via(processFlow(config, processMessage))
            .via(
              broadcastAndMerge(batchIndexAndSendWorksAndImages, noOutputFlow)
            )
      )
    } yield Done

  val batchIndexAndSendWorksAndImages
    : Flow[(Message, List[WorkOrImage]), Message, NotUsed] =
    batchIndexAndSendFlow(config, sendWorkOrImage, workOrImageIndexer)

  private def processMessage(
    message: NotificationMessage
  ): Future[List[WorkOrImage]] =
    for {
      matcherResult <- Future.fromTry(
        fromJson[MatcherResult](message.body)
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

  private def sendWorkOrImage(workOrImage: WorkOrImage): Try[Unit] =
    workOrImage match {
      case Left(work)   => workRouter(work)
      case Right(image) => imageMsgSender.send(imageIndexable.id(image))
    }
}
