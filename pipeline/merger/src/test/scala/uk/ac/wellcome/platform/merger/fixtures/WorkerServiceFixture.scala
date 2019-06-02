package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.BaseWork
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.merger.services._

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture extends RecorderVhsFixture with Messaging {
  def withWorkerService[R](vhs: RecorderVhs,
                           messageSender: MemoryBigMessageSender[BaseWork],
                           queue: Queue,
                           metricsSender: MetricsSender)(
    testWith: TestWith[MergerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](
        queue = queue,
        metricsSender = metricsSender) { sqsStream =>
        val workerService = new MergerWorkerService(
          sqsStream = sqsStream,
          playbackService = new RecorderPlaybackService(vhs),
          mergerManager = new MergerManager(PlatformMerger),
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }

  def withWorkerService[R](
    vhs: RecorderVhs,
    messageSender: MemoryBigMessageSender[BaseWork],
    queue: Queue)(testWith: TestWith[MergerWorkerService[String], R]): R =
    withMetricsSender() { metricsSender =>
      withWorkerService(vhs, messageSender, queue, metricsSender) {
        workerService =>
          testWith(workerService)
      }
    }
}
