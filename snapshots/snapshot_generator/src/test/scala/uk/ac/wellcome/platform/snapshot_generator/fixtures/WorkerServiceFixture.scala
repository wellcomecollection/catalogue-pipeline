package uk.ac.wellcome.platform.snapshot_generator.fixtures

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.sksamuel.elastic4s.Index
import org.scalatest.Suite
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.snapshot_generator.services.SnapshotGeneratorWorkerService

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends AkkaS3
    with SnapshotServiceFixture
    with SQS { this: Suite =>
  def withWorkerService[R](
    queue: Queue,
    messageSender: MemoryMessageSender,
    indexV1: Index,
    indexV2: Index)(testWith: TestWith[SnapshotGeneratorWorkerService[String], R])(
    implicit actorSystem: ActorSystem,
    materializer: ActorMaterializer): R =
    withS3AkkaClient { s3AkkaClient =>
      withSnapshotService(s3AkkaClient, indexV1, indexV2) { snapshotService =>
        withSQSStream[NotificationMessage, R](queue) { sqsStream =>
          val workerService = new SnapshotGeneratorWorkerService(
            snapshotService = snapshotService,
            sqsStream = sqsStream,
            messageSender = messageSender
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }
}
