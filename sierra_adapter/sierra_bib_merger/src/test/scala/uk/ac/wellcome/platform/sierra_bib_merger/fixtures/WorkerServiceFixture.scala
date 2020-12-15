package uk.ac.wellcome.platform.sierra_bib_merger.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.sierra_bib_merger.services.{
  SierraBibMergerUpdaterService,
  SierraBibMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

import scala.concurrent.Future

trait WorkerServiceFixture
    extends Akka
    with SierraAdapterHelpers
    with SNS
    with SQS
    with SourceVHSFixture {
  def withWorkerService[R](sourceVHS: SourceVHS[SierraTransformable] =
                             createSourceVHS[SierraTransformable],
                           queue: Queue,
                           metrics: Metrics[Future] = new MemoryMetrics())(
    testWith: TestWith[(SierraBibMergerWorkerService[String],
                        MemoryMessageSender),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      val updaterService = new SierraBibMergerUpdaterService(sourceVHS)

      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val messageSender = new MemoryMessageSender
        val workerService = new SierraBibMergerWorkerService(
          sqsStream = sqsStream,
          messageSender = messageSender,
          sierraBibMergerUpdaterService = updaterService
        )

        workerService.run()

        testWith((workerService, messageSender))
      }
    }

}
