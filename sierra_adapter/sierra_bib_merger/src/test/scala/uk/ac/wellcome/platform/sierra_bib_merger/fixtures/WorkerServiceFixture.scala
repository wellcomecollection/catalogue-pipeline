package uk.ac.wellcome.platform.sierra_bib_merger.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{NotificationStreamFixture, SNS}
import uk.ac.wellcome.models.transformable.sierra.SierraBibRecord
import uk.ac.wellcome.platform.sierra_bib_merger.services.{
  SierraBibMergerUpdaterService,
  SierraBibMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.fixtures.S3.Bucket

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends Akka
    with NotificationStreamFixture
    with SierraAdapterHelpers
    with SNS {
  def withWorkerService[R](
    bucket: Bucket,
    table: Table,
    queue: Queue,
    topic: Topic)(testWith: TestWith[SierraBibMergerWorkerService, R]): R =
    withActorSystem { implicit actorSystem =>
      withSierraVHS(bucket, table) { versionedHybridStore =>
        val updaterService = new SierraBibMergerUpdaterService(
          versionedHybridStore = versionedHybridStore
        )

        withNotificationStream[SierraBibRecord, R](queue) {
          notificationStream =>
            withSNSWriter(topic) { snsWriter =>
              val workerService = new SierraBibMergerWorkerService(
                notificationStream = notificationStream,
                snsWriter = snsWriter,
                sierraBibMergerUpdaterService = updaterService
              )

              workerService.run()

              testWith(workerService)
            }
        }
      }
    }
}
