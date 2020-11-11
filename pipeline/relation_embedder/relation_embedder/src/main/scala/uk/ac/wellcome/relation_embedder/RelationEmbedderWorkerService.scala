package uk.ac.wellcome.relation_embedder

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.WorkFsm._
import uk.ac.wellcome.models.work.internal.WorkState.Denormalised
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.Indexer
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  workIndexer: Indexer[Work[Denormalised]],
  relationsService: RelationsService,
  indexBatchSize: Int = 100,
  indexFlushInterval: FiniteDuration = 20 seconds
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable {

  def run(): Future[Done] =
    workIndexer.init().flatMap { _ =>
      sqsStream.foreach(this.getClass.getSimpleName, processMessage)
    }

  def processMessage(message: NotificationMessage): Future[Unit] ={
    val path = CollectionPath(message.body)
        relationsService
          .getAllWorksInArchive(path)
          .runWith(Sink.seq)
          .map { archiveWorks =>
            (ArchiveRelationsCache(archiveWorks), path)
          }
      .flatMap {
        case (relationsCache, inputPath) =>
          val denormalisedWorks = relationsService.getAffectedWorks(inputPath)
            .map { work =>
              work.transition[Denormalised](relationsCache(work))
            }

          denormalisedWorks
            .groupedWithin(indexBatchSize, indexFlushInterval)
            .mapAsync(2) { works =>
              workIndexer.index(works).flatMap {
                case Left(failedWorks) =>
                  Future.failed(
                    new Exception(s"Failed indexing works: $failedWorks")
                  )
                case Right(_) => Future.successful(works.toList)
              }
            }
            .mapConcat(identity)
            .mapAsync(3) { work =>
              Future(msgSender.send(work.id)).flatMap {
                case Success(_) => Future.successful(())
                case Failure(err) => Future.failed(err)
              }
            }
            .runWith(Sink.ignore)
            .map(_ => ())
      }
  }
}
