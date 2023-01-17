package weco.pipeline.sierra_reader.services

import akka.http.scaladsl.model._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.sierra_reader.exceptions.SierraReaderException
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.storage.s3.S3ObjectLocation
import weco.catalogue.source_model.sierra.SierraBibRecord
import weco.catalogue.source_model.Implicits._
import weco.messaging.sns.NotificationMessage
import weco.pipeline.sierra_reader.fixtures.WorkerServiceFixture

class SierraReaderWorkerServiceTest
    extends AnyFunSpec
    with Eventually
    with Matchers
    with IntegrationPatience
    with ScalaFutures
    with WorkerServiceFixture {

  it("fetches bibs from Sierra") {
    val body =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val responses = Seq(
      (
        HttpRequest(uri = Uri(
          s"$sierraUri/bibs?updatedDate=%5B2013-12-10T17:16:35Z,2013-12-13T21:34:35Z%5D&fields=updatedDate,deletedDate,deleted,suppressed,author,title")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "total": 16,
              |  "start": 0,
              |  "entries": [
              |    {"id": "1000001", "updatedDate": "2013-12-10T18:00:01Z"},
              |    {"id": "1000002", "updatedDate": "2013-12-10T18:00:02Z"},
              |    {"id": "1000003", "updatedDate": "2013-12-10T18:00:03Z"},
              |    {"id": "1000004", "updatedDate": "2013-12-10T18:00:04Z"},
              |    {"id": "1000005", "updatedDate": "2013-12-10T18:00:05Z"},
              |    {"id": "1000006", "updatedDate": "2013-12-10T18:00:06Z"},
              |    {"id": "1000007", "updatedDate": "2013-12-10T18:00:07Z"},
              |    {"id": "1000008", "updatedDate": "2013-12-10T18:00:08Z"},
              |    {"id": "1000009", "updatedDate": "2013-12-10T18:00:09Z"},
              |    {"id": "1000010", "updatedDate": "2013-12-10T18:00:10Z"},
              |    {"id": "1000011", "updatedDate": "2013-12-10T18:00:11Z"},
              |    {"id": "1000012", "updatedDate": "2013-12-10T18:00:12Z"},
              |    {"id": "1000013", "updatedDate": "2013-12-10T18:00:13Z"},
              |    {"id": "1000014", "updatedDate": "2013-12-10T18:00:14Z"},
              |    {"id": "1000015", "updatedDate": "2013-12-10T18:00:15Z"},
              |    {"id": "1000016", "updatedDate": "2013-12-10T18:00:16Z"}
              |  ]
              |}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(
          s"$sierraUri/bibs?updatedDate=%5B2013-12-10T17:16:35Z,2013-12-13T21:34:35Z%5D&fields=updatedDate,deletedDate,deleted,suppressed,author,title&id=%5B1000017,%5D")),
        HttpResponse(
          status = StatusCodes.NotFound,
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "code": 107,
              |  "specificCode": 0,
              |  "httpStatus": 404,
              |  "name": "Record not found"
              |}
              |""".stripMargin
          )
        )
      )
    )

    withLocalS3Bucket { bucket =>
      withLocalSqsQueue() { queue =>
        withWorkerService(
          responses,
          bucket,
          queue,
          readerConfig = bibsReaderConfig.copy(batchSize = 5)) { _ =>
          sendNotificationToSQS(queue = queue, body = body)

          val pageNames = List(
            "0000.json",
            "0001.json",
            "0002.json",
            "0003.json").map { label =>
            s"records_bibs/2013-12-10T17-16-35Z__2013-12-13T21-34-35Z/$label"
          } ++ List(
            "windows_bibs_complete/2013-12-10T17-16-35Z__2013-12-13T21-34-35Z")

          eventually {
            listKeysInBucket(bucket) shouldBe pageNames

            getBibRecordsFromS3(bucket, pageNames(0)) should have size 5
            getBibRecordsFromS3(bucket, pageNames(1)) should have size 5
            getBibRecordsFromS3(bucket, pageNames(2)) should have size 5
            getBibRecordsFromS3(bucket, pageNames(3)) should have size 1
          }
        }
      }
    }
  }

  it("resumes a window if it finds an in-progress set of records") {
    val body =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val firstPage = (
      HttpRequest(uri = Uri(
        s"$sierraUri/bibs?updatedDate=%5B2013-12-10T17:16:35Z,2013-12-13T21:34:35Z%5D&fields=updatedDate,deletedDate,deleted,suppressed,author,title")),
      HttpResponse(
        entity = HttpEntity(
          contentType = ContentTypes.`application/json`,
          """
            |{
            |  "total": 10,
            |  "start": 0,
            |  "entries": [
            |    {"id": "1000001", "updatedDate": "2013-12-10T18:00:01Z"},
            |    {"id": "1000002", "updatedDate": "2013-12-10T18:00:02Z"},
            |    {"id": "1000003", "updatedDate": "2013-12-10T18:00:03Z"},
            |    {"id": "1000004", "updatedDate": "2013-12-10T18:00:04Z"},
            |    {"id": "1000005", "updatedDate": "2013-12-10T18:00:05Z"},
            |    {"id": "1000006", "updatedDate": "2013-12-10T18:00:06Z"},
            |    {"id": "1000007", "updatedDate": "2013-12-10T18:00:07Z"},
            |    {"id": "1000008", "updatedDate": "2013-12-10T18:00:08Z"},
            |    {"id": "1000009", "updatedDate": "2013-12-10T18:00:09Z"},
            |    {"id": "1000010", "updatedDate": "2013-12-10T18:00:10Z"}
            |  ]
            |}
            |""".stripMargin
        )
      )
    )

    val secondPage = (
      HttpRequest(uri = Uri(
        s"$sierraUri/bibs?updatedDate=%5B2013-12-10T17:16:35Z,2013-12-13T21:34:35Z%5D&fields=updatedDate,deletedDate,deleted,suppressed,author,title&id=%5B1000011,%5D")),
      HttpResponse(
        entity = HttpEntity(
          contentType = ContentTypes.`application/json`,
          """
            |{
            |  "total": 6,
            |  "start": 0,
            |  "entries": [
            |    {"id": "1000011", "updatedDate": "2013-12-10T18:00:11Z"},
            |    {"id": "1000012", "updatedDate": "2013-12-10T18:00:12Z"},
            |    {"id": "1000013", "updatedDate": "2013-12-10T18:00:13Z"},
            |    {"id": "1000014", "updatedDate": "2013-12-10T18:00:14Z"},
            |    {"id": "1000015", "updatedDate": "2013-12-10T18:00:15Z"},
            |    {"id": "1000016", "updatedDate": "2013-12-10T18:00:16Z"}
            |  ]
            |}
            |""".stripMargin
        )
      )
    )

    val finalPage = (
      HttpRequest(uri = Uri(
        s"$sierraUri/bibs?updatedDate=%5B2013-12-10T17:16:35Z,2013-12-13T21:34:35Z%5D&fields=updatedDate,deletedDate,deleted,suppressed,author,title&id=%5B1000017,%5D")),
      HttpResponse(
        status = StatusCodes.NotFound,
        entity = HttpEntity(
          contentType = ContentTypes.`application/json`,
          """
            |{
            |  "code": 107,
            |  "specificCode": 0,
            |  "httpStatus": 404,
            |  "name": "Record not found"
            |}
            |""".stripMargin
        )
      )
    )

    val responses = Seq(firstPage, secondPage, finalPage, secondPage, finalPage)

    withLocalS3Bucket { bucket =>
      withLocalSqsQueue() { queue =>
        withWorkerService(
          responses,
          bucket,
          queue,
          readerConfig = bibsReaderConfig.copy(batchSize = 10)) { service =>
          // Do a complete run of the reader -- this gives us a set of JSON files
          // to compare to.
          sendNotificationToSQS(queue = queue, body = body)

          eventually {
            assertQueueEmpty(queue = queue)

            // 2 files + 1 window
            listKeysInBucket(bucket = bucket) should have size 3
          }

          val expectedContents = getAllObjectContents(bucket = bucket)

          // Now, let's delete every key in the bucket _except_ the first --
          // which we'll use to restart the window.
          listKeysInBucket(bucket = bucket)
            .filterNot {
              _.endsWith("0000.json")
            }
            .foreach { key =>
              deleteObject(S3ObjectLocation(bucket.name, key))
            }

          eventually {
            listKeysInBucket(bucket = bucket) should have size 1
          }

          // Now, send a second message to the reader, and we'll see if it completes
          // the window successfully.
          sendNotificationToSQS(queue = queue, body = body)

          eventually {
            getAllObjectContents(bucket = bucket) shouldBe expectedContents
          }
        }
      }
    }
  }

  it("returns a SierraReaderException if it receives an invalid message") {
    val body =
      """
        |{
        | "start": "2013-12-10T17:16:35Z"
        |}
      """.stripMargin

    val notificationMessage = NotificationMessage(body)

    val responses = Seq()

    withLocalS3Bucket { bucket =>
      withLocalSqsQueue() { queue =>
        withWorkerService(
          responses,
          bucket,
          queue,
          readerConfig = itemsReaderConfig) { service =>
          whenReady(service.processMessage(notificationMessage).failed) { ex =>
            ex shouldBe a[SierraReaderException]
          }
        }
      }
    }
  }

  it("doesn't return a SierraReaderException if it cannot reach the Sierra API") {
    val body =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val responses = Seq()

    val notificationMessage = NotificationMessage(body)

    withLocalS3Bucket { bucket =>
      withLocalSqsQueue() { queue =>
        withWorkerService(responses, bucket, queue) { service =>
          val future = service.processMessage(notificationMessage)

          whenReady(future.failed) {
            _ shouldNot be(a[SierraReaderException])
          }
        }
      }
    }
  }

  private def getBibRecordsFromS3(bucket: Bucket,
                                  key: String): List[SierraBibRecord] =
    getObjectFromS3[List[SierraBibRecord]](
      S3ObjectLocation(bucket = bucket.name, key = key))
}
