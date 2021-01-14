package uk.ac.wellcome.platform.merger.services

import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import akka.Done
import io.circe.Encoder

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.models.work.internal.WorkState.{Identified, Merged}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.{Indexer, PipelineStorageStream, PipelineStorageConfig}
import uk.ac.wellcome.typesafe.Runnable

class MergerWorkerService[WorkDestination, ImageDestination](
  msgStream: SQSStream[NotificationMessage],
  workMsgSender: MessageSender[WorkDestination],
  config: PipelineStorageConfig,
  workIndexer: Indexer[Work[Merged]],
  sourceWorkLookup: IdentifiedWorkLookup,
  mergerManager: MergerManager,
  imageSender: MessageSender[ImageDestination]
)(implicit ec: ExecutionContext)
    extends Runnable {

  import PipelineStorageStream._

  def run(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      source =>
        source
          .via(processFlow(config, processMessage))
          .via(
            broadcastAndMerge(
              batchIndexAndSendFlow(config, workMsgSender, workIndexer),
              identityFlow)
            )
    )

  private def processMessage(
    message: NotificationMessage): Future[List[Work[Merged]]] =
    for {
      matcherResult <- Future.fromTry(fromJson[MatcherResult](message.body))
      workSets <- Future.sequence {
        matcherResult.works.toList.map {
          matchedIdentifiers: MatchedIdentifiers =>
            sourceWorkLookup.fetchAllWorks(
              matchedIdentifiers.identifiers.toList)
        }
      }
      nonEmptyWorkSets = workSets.filter(_.flatten.nonEmpty)
      worksToSend <- nonEmptyWorkSets match {
        case Nil => Future.successful(Nil)
        case _ =>
          val lastUpdated = nonEmptyWorkSets
            .flatMap(_.flatten.map(work => work.state.modifiedTime))
            .max
          Future.sequence {
            nonEmptyWorkSets.map(applyMerge(_, lastUpdated))
          }
      }
    } yield worksToSend.flatten

  private def applyMerge(maybeWorks: Seq[Option[Work[Identified]]],
                         lastUpdated: Instant): Future[Seq[Work[Merged]]] = {
    val outcome = mergerManager.applyMerge(maybeWorks = maybeWorks)

    for {
      _ <- sendMessages(imageSender, outcome.mergedImagesWithTime(lastUpdated))
    } yield outcome.mergedWorksWithTime(lastUpdated)
  }

  private def sendMessages[Destination, T](
    sender: MessageSender[Destination],
    items: Seq[T])(implicit encoder: Encoder[T]): Future[Seq[Unit]] =
    Future.sequence(
      items.map { item =>
        Future.fromTry(sender.sendT(item))
      }
    )
}
