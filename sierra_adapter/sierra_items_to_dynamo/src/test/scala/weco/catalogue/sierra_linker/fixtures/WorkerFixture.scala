package weco.catalogue.sierra_linker.fixtures

import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraHoldingsNumber,
  SierraHoldingsRecord,
  SierraItemNumber,
  SierraItemRecord,
  TypedSierraRecordNumber
}
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_linker.models.{Link, LinkOps}
import weco.catalogue.sierra_linker.services.{LinkStore, SierraLinkerWorker}

import scala.concurrent.Future

trait WorkerFixture extends SQS with Akka {

  import LinkOps._

  def withWorker[Id <: TypedSierraRecordNumber, Record <: AbstractSierraRecord[Id], R](
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
  )(testWith: TestWith[SierraLinkerWorker[SierraItemNumber, SierraItemRecord, String], R]): R =
    withWorker[SierraItemNumber, SierraItemRecord, R](queue, store, metrics, messageSender) { worker =>
      testWith(worker)
    }

  def withHoldingsWorker[R](
    queue: Queue,
    store: MemoryVersionedStore[SierraHoldingsNumber, Link] =
      MemoryVersionedStore[SierraHoldingsNumber, Link](initialEntries = Map.empty),
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[SierraLinkerWorker[SierraHoldingsNumber, SierraHoldingsRecord, String], R]): R =
    withWorker[SierraHoldingsNumber, SierraHoldingsRecord, R](queue, store, metrics, messageSender) { worker =>
      testWith(worker)
    }
}
