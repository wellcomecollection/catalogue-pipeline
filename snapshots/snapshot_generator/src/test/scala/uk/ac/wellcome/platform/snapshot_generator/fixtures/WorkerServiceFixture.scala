package uk.ac.wellcome.platform.snapshot_generator.fixtures

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.sksamuel.elastic4s.Index
import org.scalatest.Suite
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig, SNSMessageSender}
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.platform.snapshot_generator.services.SnapshotGeneratorWorkerService
import uk.ac.wellcome.fixtures.TestWith

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends AkkaS3
    with SnapshotServiceFixture
    with SNS
    with SQS { this: Suite =>
  def withWorkerService[R](queue: Queue, topic: Topic, worksIndex: Index)(
    testWith: TestWith[SnapshotGeneratorWorkerService, R])(
    implicit actorSystem: ActorSystem,
    materializer: Materializer): R =
    withS3AkkaSettings { s3AkkaClient =>
      withSnapshotService(s3AkkaClient, worksIndex) { snapshotService =>
        withSQSStream[NotificationMessage, R](queue) { sqsStream =>
          withSNSMessageSender(topic) { messageSender =>
            val workerService = new SnapshotGeneratorWorkerService(
              snapshotService = snapshotService,
              sqsStream = sqsStream,
              snsWriter = messageSender
            )

            workerService.run()

            testWith(workerService)
          }
        }
      }
    }

  def withSNSMessageSender[R](topic: Topic)(testWith: TestWith[SNSMessageSender,R]): R = {
    testWith(new SNSMessageSender(snsClient, SNSConfig(topic.arn), ""))
  }
}
