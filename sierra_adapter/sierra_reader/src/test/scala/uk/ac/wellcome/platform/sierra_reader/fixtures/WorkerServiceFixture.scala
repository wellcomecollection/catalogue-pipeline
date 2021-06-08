package uk.ac.wellcome.platform.sierra_reader.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.sierra_reader.config.models.{
  ReaderConfig,
  SierraAPIConfig
}
import uk.ac.wellcome.platform.sierra_reader.services.SierraReaderWorkerService
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import weco.catalogue.source_model.sierra.identifiers.SierraRecordTypes

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture
    extends Akka
    with SQS
    with S3Fixtures
    with WireMockFixture {
  def withWorkerService[R](bucket: Bucket,
                           queue: Queue,
                           readerConfig: ReaderConfig = bibsReaderConfig,
                           sierraAPIConfig: SierraAPIConfig = sierraAPIConfig)(
    testWith: TestWith[SierraReaderWorkerService, R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new SierraReaderWorkerService(
          sqsStream = sqsStream,
          s3Config = createS3ConfigWith(bucket),
          readerConfig = readerConfig,
          sierraAPIConfig = sierraAPIConfig
        )

        workerService.run()

        testWith(workerService)
      }
    }

  val bibsReaderConfig: ReaderConfig = ReaderConfig(
    recordType = SierraRecordTypes.bibs,
    fields = "updatedDate,deletedDate,deleted,suppressed,author,title"
  )

  val itemsReaderConfig: ReaderConfig = ReaderConfig(
    recordType = SierraRecordTypes.items,
    fields = "updatedDate,deleted,deletedDate,bibIds,fixedFields,varFields"
  )

  val holdingsReaderConfig: ReaderConfig = ReaderConfig(
    recordType = SierraRecordTypes.holdings,
    fields = "updatedDate"
  )
}
