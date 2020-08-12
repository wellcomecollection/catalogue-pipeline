package uk.ac.wellcome.platform.merger.fixtures

import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.merger.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WorkerServiceFixture extends LocalWorksVhs with SQS with Akka {
  def withWorkerService[R](
    vhs: VHS,
    queue: Queue,
    workSender: MemoryMessageSender,
    imageSender: MemoryMessageSender = new MemoryMessageSender(),
    metrics: Metrics[Future, StandardUnit] = new MemoryMetrics[StandardUnit])(
    testWith: TestWith[MergerWorkerService[String, String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metrics) { sqsStream =>
        val workerService = new MergerWorkerService(
          sqsStream = sqsStream,
          playbackService = new RecorderPlaybackService(vhs),
          mergerManager = new MergerManager(PlatformMerger),
          workSender = workSender,
          imageSender = imageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def withWorkerService[R](vhs: VHS)(
    testWith: TestWith[MergerWorkerService[String, String], R]): R =
    withLocalSqsQueue() { queue =>
      val workSender = new MemoryMessageSender()
      val imageSender = new MemoryMessageSender()

      withWorkerService(vhs, queue, workSender, imageSender) { workerService =>
        testWith(workerService)
      }
    }
}
