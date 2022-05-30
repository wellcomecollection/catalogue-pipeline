package weco.pipeline.sierra_reader.fixtures

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import com.amazonaws.services.s3.AmazonS3
import weco.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.Queue
import weco.messaging.sns.NotificationMessage
import weco.pipeline.sierra_reader.config.models.ReaderConfig
import weco.pipeline.sierra_reader.services.SierraReaderWorkerService
import weco.storage.fixtures.S3Fixtures
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.http.client.{HttpGet, MemoryHttpClient}
import weco.sierra.models.identifiers.SierraRecordTypes

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture extends Akka with SQS with S3Fixtures {
  val sierraUri = "http://sierra:1234"

  // TODO: We're overriding these values while scala-libs is still tied to scality/s3server,
  // but when we update it to use localstack, we can remove these.
  // See https://github.com/wellcomecollection/platform/issues/5547
  override val s3Port: Int = 4566
  override implicit val s3Client: AmazonS3 =
    createS3ClientWithEndpoint(s"http://localhost:$s3Port")

  def createClient(responses: Seq[(HttpRequest, HttpResponse)]): HttpGet =
    new MemoryHttpClient(responses) with HttpGet {
      override val baseUri: Uri = Uri(sierraUri)
    }

  def withWorkerService[R](
    responses: Seq[(HttpRequest, HttpResponse)],
    bucket: Bucket,
    queue: Queue,
    readerConfig: ReaderConfig = bibsReaderConfig
  )(testWith: TestWith[SierraReaderWorkerService, R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new SierraReaderWorkerService(
          client = createClient(responses),
          sqsStream = sqsStream,
          s3Config = createS3ConfigWith(bucket),
          readerConfig = readerConfig
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
