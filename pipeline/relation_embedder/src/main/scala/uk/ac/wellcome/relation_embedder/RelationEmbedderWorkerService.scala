package uk.ac.wellcome.relation_embedder

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
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
import WorkState.{Denormalised, Merged}
import WorkFsm._

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[Work[Merged]],
  workIndexer: Indexer[Work[Denormalised]],
  relationsService: RelationsService,
  batchSize: Int = 20,
  flushInterval: FiniteDuration = 3 seconds
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable {

  def run(): Future[Done] =
    workIndexer.init().flatMap { _ =>
      sqsStream.foreach(this.getClass.getSimpleName, processMessage)
    }

  def processMessage(message: NotificationMessage): Future[Unit] =
    Source
      .future(workRetriever(message.body))
      .mapAsync(1) { work =>
        relationsService
          .getOtherAffectedWorks(work)
          .map(work.sourceIdentifier :: _)
      }
      .mapConcat(identity)
      .mapAsync(2) { sourceIdentifier =>
        workRetriever(sourceIdentifier.toString)
      }
      .mapAsync(2) { work =>
        relationsService.getRelations(work).map(work.transition[Denormalised])
      }
      .groupedWithin(batchSize, flushInterval)
      .mapAsync(2) { work =>
        workIndexer.index(work).flatMap {
          case Left(failedWorks) =>
            Future.failed(
              new Exception(s"Failed indexing works: $failedWorks")
            )
          case Right(works) => Future.successful(works.toList)
        }
      }
      .mapConcat(identity)
      .mapAsync(2) { work =>
        Future(msgSender.send(work.id)).flatMap {
          case Success(_)   => Future.successful(())
          case Failure(err) => Future.failed(err)
        }
      }
      .runWith(Sink.ignore)
      .map(_ => ())
}
