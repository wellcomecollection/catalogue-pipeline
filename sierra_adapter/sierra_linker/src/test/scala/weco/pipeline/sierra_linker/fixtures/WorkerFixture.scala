package weco.pipeline.sierra_linker.fixtures

import io.circe.{Decoder, Encoder}
import weco.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.monitoring.Metrics
import weco.monitoring.memory.MemoryMetrics
import weco.storage.store.memory.MemoryVersionedStore
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord
}
import weco.pipeline.sierra_linker.models.{Link, LinkOps}
import weco.pipeline.sierra_linker.services.{LinkStore, SierraLinkerWorker}
import weco.sierra.models.identifiers.{
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber,
  TypedSierraRecordNumber
}

import scala.concurrent.Future

trait WorkerFixture extends SQS with Akka {

  import weco.pipeline.sierra_linker.models.LinkOps._

  def withWorker[Id <: TypedSierraRecordNumber,
                 Record <: AbstractSierraRecord[Id],
                 R](
    queue: Queue,
    store: MemoryVersionedStore[Id, Link] =
      MemoryVersionedStore[Id, Link](initialEntries = Map.empty),
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[SierraLinkerWorker[Id, Record, String], R])(
    implicit
    linkOps: LinkOps[Record],
    decoder: Decoder[Record],
    encoder: Encoder[Record]
  ): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val worker = new SierraLinkerWorker(
          sqsStream = sqsStream,
          linkStore = new LinkStore(store),
          messageSender = messageSender
        )

        worker.run()

        testWith(worker)
      }
    }

  def withItemWorker[R](
    queue: Queue,
    store: MemoryVersionedStore[SierraItemNumber, Link] =
      MemoryVersionedStore[SierraItemNumber, Link](initialEntries = Map.empty),
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[
      SierraLinkerWorker[SierraItemNumber, SierraItemRecord, String],
      R]): R =
    withWorker[SierraItemNumber, SierraItemRecord, R](
      queue,
      store,
      metrics,
      messageSender) { worker =>
      testWith(worker)
    }

  def withHoldingsWorker[R](
    queue: Queue,
    store: MemoryVersionedStore[SierraHoldingsNumber, Link] =
      MemoryVersionedStore[SierraHoldingsNumber, Link](
        initialEntries = Map.empty),
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[
      SierraLinkerWorker[SierraHoldingsNumber, SierraHoldingsRecord, String],
      R]): R =
    withWorker[SierraHoldingsNumber, SierraHoldingsRecord, R](
      queue,
      store,
      metrics,
      messageSender) { worker =>
      testWith(worker)
    }

  def withOrderWorker[R](
    queue: Queue,
    store: MemoryVersionedStore[SierraOrderNumber, Link] =
      MemoryVersionedStore[SierraOrderNumber, Link](initialEntries = Map.empty),
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[
      SierraLinkerWorker[SierraOrderNumber, SierraOrderRecord, String],
      R]): R =
    withWorker[SierraOrderNumber, SierraOrderRecord, R](
      queue,
      store,
      metrics,
      messageSender) { worker =>
      testWith(worker)
    }
}
