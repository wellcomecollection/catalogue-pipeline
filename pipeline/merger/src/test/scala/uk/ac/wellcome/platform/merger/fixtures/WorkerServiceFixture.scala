package uk.ac.wellcome.platform.merger.fixtures

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.amazonaws.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.internal.{BaseWork, MergedImage, Unminted}
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics

trait WorkerServiceFixture extends LocalWorksVhs {

  def withWorkerService[R](vhs: VHS,
                           queue: Queue,
                           worksTopic: Topic,
                           imagesTopic: Topic,
                           metrics: Metrics[Future, StandardUnit] =
                             new MemoryMetrics[StandardUnit])(
    testWith: TestWith[MergerWorkerService[SNSConfig, SNSConfig], R]): R =
    withLocalS3Bucket { bucket =>
      withSqsBigMessageSender[BaseWork, R](bucket, worksTopic) { workSender =>
        withSqsBigMessageSender[MergedImage[Unminted], R](bucket, imagesTopic) {
          imageSender =>
            withActorSystem { implicit actorSystem =>
              withSQSStream[NotificationMessage, R](queue, metrics) {
                sqsStream =>
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
        }
      }
    }

  def withWorkerService[R](vhs: VHS)(
    testWith: TestWith[MergerWorkerService[SNSConfig, SNSConfig], R]): R =
    withLocalSqsQueue { queue =>
      withLocalSnsTopic { worksTopic =>
        withLocalSnsTopic { imagesTopic =>
          withWorkerService(vhs, queue, worksTopic, imagesTopic) {
            workerService =>
              testWith(workerService)
          }
        }
      }
    }
}
