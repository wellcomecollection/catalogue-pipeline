package uk.ac.wellcome.platform.sierra_reader

import akka.http.scaladsl.model._
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.sierra_reader.fixtures.WorkerServiceFixture

class SierraReaderFeatureTest
    extends AnyFunSpec
    with Eventually
    with Matchers
    with IntegrationPatience
    with WorkerServiceFixture {

  it("reads bibs from Sierra and writes files to S3") {
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
        withWorkerService(responses, bucket, queue) { service =>
          service.run()

          sendNotificationToSQS(queue = queue, body = body)

          eventually {
            listKeysInBucket(bucket) should have size 2
          }
        }
      }
    }
  }
}
