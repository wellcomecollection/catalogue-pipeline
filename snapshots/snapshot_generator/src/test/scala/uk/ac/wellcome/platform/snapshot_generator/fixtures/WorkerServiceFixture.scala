package uk.ac.wellcome.platform.snapshot_generator.fixtures

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{NotificationStreamFixture, SNS}
import uk.ac.wellcome.platform.snapshot_generator.models.SnapshotJob
import uk.ac.wellcome.platform.snapshot_generator.services.SnapshotGeneratorWorkerService

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends AkkaS3
    with NotificationStreamFixture
    with SnapshotServiceFixture
    with SNS {
  def withWorkerService[R](
    queue: Queue,
    topic: Topic,
    indexV1: Index,
    indexV2: Index)(testWith: TestWith[SnapshotGeneratorWorkerService, R])(
    implicit actorSystem: ActorSystem,
    materializer: ActorMaterializer): R =
    withS3AkkaClient { s3AkkaClient =>
      withSnapshotService(s3AkkaClient, indexV1, indexV2) { snapshotService =>
        withNotificationStream[SnapshotJob, R](queue) { notificationStream =>
          withSNSWriter(topic) { snsWriter =>
            val workerService = new SnapshotGeneratorWorkerService(
              notificationStream = notificationStream,
              snapshotService = snapshotService,
              snsWriter = snsWriter
            )

            workerService.run()

            testWith(workerService)
          }
        }
      }
    }
}
