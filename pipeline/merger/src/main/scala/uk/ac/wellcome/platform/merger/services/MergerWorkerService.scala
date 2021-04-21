package uk.ac.wellcome.platform.merger.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import java.time.Instant
import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.MatcherResult
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.{
  Indexable,
  Indexer,
  PipelineStorageConfig,
  PipelineStorageStream
}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work

class MergerWorkerService[WorkDestination, ImageDestination](
  msgStream: SQSStream[NotificationMessage],
  sourceWorkLookup: IdentifiedWorkLookup,
  mergerManager: MergerManager,
  workOrImageIndexer: Indexer[Either[Work[Merged], Image[Initial]]],
  workMsgSender: MessageSender[WorkDestination],
  imageMsgSender: MessageSender[ImageDestination],
  config: PipelineStorageConfig
)(implicit ec: ExecutionContext)
    extends Runnable {

  import PipelineStorageStream._
  import Indexable._

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
              broadcastAndMerge(batchIndexAndSendWorksAndImages, noOutputFlow))
      )
    } yield Done

  val batchIndexAndSendWorksAndImages
    : Flow[(Message, List[WorkOrImage]), Message, NotUsed] =
    batchIndexAndSendFlow(config, sendWorkOrImage, workOrImageIndexer)

  private def processMessage(
    message: NotificationMessage): Future[List[WorkOrImage]] =
    for {
      matcherResult <- Future.fromTry(
        fromJson[MatcherResult](message.body)
      )

      workSets <-
        getWorkSets(matcherResult)
          .map(workSets => workSets.filter(_.flatten.nonEmpty))

      result = workSets match {
        case Nil => Nil
        case workSets =>
          val lastUpdated = getLastUpdated(workSets)
          workSets.flatMap(workSet => applyMerge(workSet, lastUpdated))
      }
    } yield result

  private def getWorkSets(matcherResult: MatcherResult): Future[List[WorkSet]] =
    Future.sequence {
      matcherResult.works.toList.map { matchedIdentifiers =>
        sourceWorkLookup.fetchAllWorks(matchedIdentifiers.identifiers.toList)
      }
    }

  private def applyMerge(workSet: WorkSet,
                         lastUpdated: Instant): Seq[WorkOrImage] =
    mergerManager
      .applyMerge(maybeWorks = workSet)
      .mergedWorksAndImagesWithTime(lastUpdated)

  private def sendWorkOrImage(workOrImage: WorkOrImage): Try[Unit] =
    workOrImage match {
      case Left(work)   => workMsgSender.send(workIndexable.id(work))
      case Right(image) => imageMsgSender.send(imageIndexable.id(image))
    }

  private def getLastUpdated(workSets: List[WorkSet]): Instant =
    workSets
      .flatMap(_.flatten.map(work => work.state.modifiedTime))
      .max
}
