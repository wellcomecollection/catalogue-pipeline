package uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.{
  SierraItemLinkStore,
  SierraItemsToDynamoWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.SierraItemNumber
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_linker.models.Link

import scala.concurrent.Future

trait WorkerServiceFixture extends SQS with Akka {

  def withWorkerService[R](
    queue: Queue,
    store: MemoryVersionedStore[SierraItemNumber, Link] =
      MemoryVersionedStore[SierraItemNumber, Link](
        initialEntries = Map.empty),
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[SierraItemsToDynamoWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val workerService = new SierraItemsToDynamoWorkerService[String](
          sqsStream = sqsStream,
          itemLinkStore = new SierraItemLinkStore(store),
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
