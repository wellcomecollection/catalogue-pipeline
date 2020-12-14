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
  DynamoInserter,
  SierraItemsToDynamoWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

import scala.concurrent.Future

trait WorkerServiceFixture
    extends SQS
    with Akka
    with SourceVHSFixture {

  def withWorkerService[R](
    queue: Queue,
    sourceVHS: SourceVHS[SierraItemRecord] = createSourceVHS[SierraItemRecord],
    metrics: Metrics[Future] = new MemoryMetrics()
  )(testWith: TestWith[(SierraItemsToDynamoWorkerService[String],
                        MemoryMessageSender),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val messageSender = new MemoryMessageSender

        val workerService = new SierraItemsToDynamoWorkerService[String](
          sqsStream = sqsStream,
          dynamoInserter = new DynamoInserter(sourceVHS),
          messageSender = messageSender
        )

        workerService.run()

        testWith((workerService, messageSender))
      }
    }
}
