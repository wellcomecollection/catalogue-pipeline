package uk.ac.wellcome.relation_embedder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl._
import grizzled.slf4j.Logging

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.WorkFsm._
import uk.ac.wellcome.models.work.internal.WorkState.Denormalised
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.Indexer
import uk.ac.wellcome.typesafe.Runnable

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  workIndexer: Indexer[Work[Denormalised]],
  relationsService: RelationsService,
  indexBatchSize: Int = 100,
  indexFlushInterval: FiniteDuration = 20 seconds
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable
    with Logging {

  def run(): Future[Done] =
    workIndexer.init().flatMap { _ =>
      sqsStream.foreach(this.getClass.getSimpleName, processMessage)
    }

  def processMessage(message: NotificationMessage): Future[Unit] =
    Future
      .fromTry(fromJson[Batch](message.body))
      .map { batch =>
        info(
          s"Received batch for tree ${batch.rootPath} containing ${batch.selectors.size} selectors: $selectors")
        relationsService
          .getCompleteTree(batch)
          .runWith(Sink.seq)
          .map(ArchiveRelationsCache(_))
          .flatMap { relationsCache =>
            info(
              s"Built cache for tree ${batch.rootPath}, containing ${relationsCache.size} relations (${relationsCache.numParents} works map to parent works).")
            val denormalisedWorks = relationsService
              .getAffectedWorks(batch)
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
                  case Success(_)   => Future.successful(())
                  case Failure(err) => Future.failed(err)
                }
              }
              .runWith(Sink.ignore)
              .map(_ => ())
          }
      }
}
