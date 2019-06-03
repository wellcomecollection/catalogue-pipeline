package uk.ac.wellcome.platform.sierra_bib_merger.fixtures

import com.amazonaws.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.sierra_bib_merger.services.{SierraBibMergerUpdaterService, SierraBibMergerWorkerService}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers

trait WorkerServiceFixture extends Akka with SQS with SierraAdapterHelpers {
  def withWorkerService[R](vhs: SierraVHS,
                           queue: Queue,
                           messageSender: MemoryMessageSender,
                           metrics: MemoryMetrics[StandardUnit] = new MemoryMetrics[StandardUnit]())(
    testWith: TestWith[SierraBibMergerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      val updaterService = new SierraBibMergerUpdaterService(vhs)

      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val workerService = new SierraBibMergerWorkerService(
          sqsStream = sqsStream,
          messageSender = messageSender,
          sierraBibMergerUpdaterService = updaterService
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
