package uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.SierraItemsToDynamoWorkerService
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.store.VersionedStore

import scala.concurrent.Future

trait WorkerServiceFixture
    extends SNS
    with SQS
    with DynamoInserterFixture
    with Akka {

  def withWorkerService[R](
    queue: Queue,
    store: VersionedStore[String, Int, SierraItemRecord],
    metricsSender: Metrics[Future] = new MemoryMetrics())(
    testWith: TestWith[(SierraItemsToDynamoWorkerService[String],
                        MemoryMessageSender),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metricsSender) { sqsStream =>
        withDynamoInserter(store) { dynamoInserter =>
          withSNSMessageSender { snsWriter =>
            val workerService = new SierraItemsToDynamoWorkerService[String](
              sqsStream = sqsStream,
              dynamoInserter = dynamoInserter,
              messageSender = snsWriter
            )

            workerService.run()

            testWith((workerService, snsWriter))
          }
        }
      }
    }

  def withSNSMessageSender[R](testWith: TestWith[MemoryMessageSender, R]): R = {
    testWith(new MemoryMessageSender)
  }
}
