package weco.pipeline.relation_embedder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl._
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.catalogue.internal_model.work.WorkFsm._
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.typesafe.Runnable
import weco.pipeline_storage.Indexable.workIndexable
import weco.catalogue.internal_model.work.Work
import weco.pipeline.relation_embedder.models.{ArchiveRelationsCache, Batch}
import weco.pipeline_storage.Indexer

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

  def processMessage(message: NotificationMessage): Future[Unit] = {
    val batch = fromJson[Batch](message.body)
    Future
      .fromTry(batch)
      .flatMap { batch =>
        info(
          s"Received batch for tree ${batch.rootPath} containing ${batch.selectors.size} selectors: ${batch.selectors
            .mkString(", ")}")
        relationsService
          .getRelationTree(batch)
          .runWith(Sink.seq)
          .map { relationWorks =>
            info(
              s"Received ${relationWorks.size} relations for tree ${batch.rootPath}")
            ArchiveRelationsCache(relationWorks)
          }
          .flatMap { relationsCache =>
            info(
              s"Built cache for tree ${batch.rootPath}, containing ${relationsCache.size} relations (${relationsCache.numParents} works map to parent works).")
            val denormalisedWorks = relationsService
              .getAffectedWorks(batch)
              .map { work =>
                val relations = relationsCache(work)
                val relationAvailabilities =
                  relationsCache.getAvailabilities(work)
                work.transition[Denormalised](
                  (relations, relationAvailabilities)
                )
              }

            denormalisedWorks
              .groupedWeightedWithin(
                indexBatchSize,
                indexFlushInterval
              )(workIndexable.weight)
              .mapAsync(1) { works =>
                workIndexer(works).flatMap {
                  case Left(failedWorks) =>
                    Future.failed(
                      new Exception(s"Failed indexing works: $failedWorks")
                    )
                  case Right(_) => Future.successful(works.toList)
                }
              }
              .mapConcat(_.map(_.id))
              .mapAsync(3) { id =>
                Future(msgSender.send(id)).flatMap {
                  case Right(_)  => Future.successful(())
                  case Left(err) => Future.failed(err.e)
                }
              }
              .runWith(Sink.ignore)
              .map(_ => ())
          }
      }
      .recoverWith {
        case err =>
          val batchString =
            batch.map(_.toString).getOrElse("could not parse message")
          error(s"Failed processing batch: $batchString", err)
          Future.failed(err)
      }
  }
}
