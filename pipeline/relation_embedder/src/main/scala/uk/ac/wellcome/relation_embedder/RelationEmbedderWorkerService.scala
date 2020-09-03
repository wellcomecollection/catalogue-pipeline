package uk.ac.wellcome.relation_embedder

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.Done
import akka.stream.scaladsl._
import akka.stream.Materializer

import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.json.JsonUtil._

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[IdentifiedBaseWork],
  workIndexer: Indexer[IdentifiedBaseWork],
  relatedWorksService: RelatedWorksService,
  batchSize: Int = 20,
  flushInterval: FiniteDuration = 3 seconds
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable {
  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(message: NotificationMessage): Future[Unit] =
    Source
      .future(workRetriever(message.body))
      .mapAsync(1) { work =>
        relatedWorksService
          .getOtherAffectedWorks(work)
          .map(work.sourceIdentifier :: _)
      }
      .mapConcat(sourceIdentifier => sourceIdentifier)
      .mapAsync(3) { sourceIdentifier =>
        workRetriever(sourceIdentifier.toString)
      }
      .mapAsync(3) { work =>
        relatedWorksService
          .getRelations(work)
          .map(relations => (work, relations))
      }
      .map {
        case (work, relations) =>
          // TODO: here we should add the relations to the work model for storage.
          // This requires model changes which have not been made yet
          val denormalisedWork: IdentifiedBaseWork = ???
          denormalisedWork
      }
      .groupedWithin(batchSize, flushInterval)
      .map { works =>
        workIndexer.index(works)
      }
      .runForeach(_ => ())
      .map(_ => ())
}
