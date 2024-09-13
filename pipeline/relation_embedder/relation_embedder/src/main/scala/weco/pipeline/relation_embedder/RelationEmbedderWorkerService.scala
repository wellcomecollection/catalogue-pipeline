package weco.pipeline.relation_embedder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl._
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
import weco.pipeline.relation_embedder.models.Selector._
import weco.pipeline.relation_embedder.models.{ArchiveRelationsCache, Batch}
import weco.pipeline_storage.Indexer

trait Worker[T, Output] {
  def doWork(t: T): Output
}

trait RelationEmbedderWorker[MsgDestination]
    extends Worker[Batch, Future[Unit]]
    with Logging {

  implicit val ec: ExecutionContext
  implicit val materializer: Materializer

  val relationsService: RelationsService
  val workIndexer: Indexer[Work[Denormalised]]
  val msgSender: MessageSender[MsgDestination]

  val indexBatchSize: Int
  val indexFlushInterval: FiniteDuration

  private def denormaliseAll(
                              batch: Batch,
                              relationsCache: ArchiveRelationsCache
                            ): Source[Work[Denormalised], NotUsed] =
    relationsService
      .getAffectedWorks(batch)
      .map {
        work =>
          val relations = relationsCache(work)
          work.transition[Denormalised](relations)
      }

  private def indexWorks(
                          denormalisedWorks: Source[Work[Denormalised], NotUsed]
                        ) =
    denormalisedWorks
      .groupedWeightedWithin(
        indexBatchSize,
        indexFlushInterval
      )(workIndexable.weight)
      .mapAsync(1) {
        works =>
            workIndexer(works).flatMap {
              case Left(failedWorks) =>
                Future.failed(
                  new Exception(s"Failed indexing works: $failedWorks")
                )
              case Right(_) => Future.successful(works.toList)

          }

      }
      .mapConcat(_.map(_.id))
      .mapAsync(3) {
        id =>
          Future(msgSender.send(id)).flatMap {
            case Success(_)   => Future.successful(())
            case Failure(err) => Future.failed(err)
          }
      }
      .runWith(Sink.ignore)
      .map(_ => ())

  private def fetchRelations(batch: Batch): Future[ArchiveRelationsCache] =
    relationsService
      .getRelationTree(batch)
      .runWith(Sink.seq)
      .map {
        relationWorks =>
          info(
            s"Received ${relationWorks.size} relations for tree ${batch.rootPath}"
          )
          ArchiveRelationsCache(relationWorks)
      }

  def doWork(batch: Batch): Future[Unit] = {
    info(
      s"Received batch for tree ${batch.rootPath} containing ${batch.selectors.size} selectors: ${batch.selectors
        .mkString(", ")}"
    )
    fetchRelations(batch)
      .flatMap {
        relationsCache =>
          info(
            s"Built cache for tree ${batch.rootPath}, containing ${relationsCache.size} relations (${relationsCache.numParents} works map to parent works)."
          )
          indexWorks(denormaliseAll(batch, relationsCache))
      }
  }
}

class CommandLineRelationEmbedderWorkerService[MsgDestination](
    val relationsService: RelationsService,
    val workIndexer: Indexer[Work[Denormalised]],
    val msgSender: MessageSender[MsgDestination]
)(path: String)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
)
    extends Runnable
      with RelationEmbedderWorker[MsgDestination] {

  def runWithPaths(path: String): Future[Done] = {
    val batch = Batch(path.split("/").head, List(Node(path)))
    info(s"Running with batch: $batch")
    doWork(batch).map(_ => Done)
  }

  def run(): Future[Done] =
    runWithPaths(path).map(_ => Done)

  override val indexBatchSize: Int = 1
  override val indexFlushInterval: FiniteDuration = 1 seconds
}

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  val msgSender: MessageSender[MsgDestination],
  val workIndexer: Indexer[Work[Denormalised]],
  val relationsService: RelationsService,
  val indexBatchSize: Int = 100,
  val indexFlushInterval: FiniteDuration = 20 seconds
)(implicit val ec: ExecutionContext, val materializer: Materializer)
    extends Runnable
      with RelationEmbedderWorker[MsgDestination] {

  def run(): Future[Done] =
    workIndexer.init().flatMap {
      _ =>
        sqsStream.foreach(this.getClass.getSimpleName, processMessage)
    }

  def processMessage(message: NotificationMessage): Future[Unit] = {
    val batch = fromJson[Batch](message.body)
    Future
      .fromTry(batch)
      .flatMap(doWork)
      .recoverWith {
        case err =>
          val batchString =
            batch.map(_.toString).getOrElse("could not parse message")
          error(s"Failed processing batch: $batchString", err)
          Future.failed(err)
      }
  }
}
