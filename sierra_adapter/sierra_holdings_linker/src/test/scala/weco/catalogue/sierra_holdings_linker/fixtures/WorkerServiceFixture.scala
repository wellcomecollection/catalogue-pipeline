package weco.catalogue.sierra_holdings_linker.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import weco.catalogue.sierra_holdings_linker.services.SierraHoldingsLinkerWorkerService

import scala.concurrent.Future

trait WorkerServiceFixture extends SQS with Akka {

  def withWorkerService[R](
    queue: Queue,
    metrics: Metrics[Future] = new MemoryMetrics(),
    messageSender: MemoryMessageSender = new MemoryMessageSender
  )(testWith: TestWith[SierraHoldingsLinkerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val workerService = new SierraHoldingsLinkerWorkerService[String](
          sqsStream = sqsStream,
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
