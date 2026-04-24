package weco.pipeline.mets_adapter.services

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pekko.fixtures.Pekko
import weco.fixtures.TestWith
import weco.pipeline.mets_adapter.models._
import weco.http.client.{HttpClient, HttpGet, MemoryHttpClient}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class BagRetrieverTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Pekko {

  it("gets a bag from the storage service") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b16237456")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "id": "digitised/b16237456",
              |  "space": {
              |    "id": "digitised",
              |    "type": "Space"
              |  },
              |  "info": {
              |    "externalIdentifier": "b16237456",
              |    "payloadOxum": "1138856615.297",
              |    "baggingDate": "2021-05-19",
              |    "sourceOrganization": "intranda GmbH",
              |    "externalDescription": "[The Caldecott Community]",
              |    "internalSenderIdentifier": "4656",
              |    "internalSenderDescription": "sa_eug_d_51_box_35_b16237456",
              |    "type": "BagInfo"
              |  },
              |  "manifest": {
              |    "checksumAlgorithm": "SHA-256",
              |    "files": [
              |      {
              |        "checksum": "00c28fa37208820ff9e621092fe522ef49b7dfe38f027a370b02666efff017a1",
              |        "name": "data/b16237456.xml",
              |        "path": "v3/data/b16237456.xml",
              |        "size": 758093,
              |        "type": "File"
              |      },
              |      {
              |        "checksum": "30266128984e9e702aebc4e2ca89c56ac189818f4e328d1e7bb8570e14acdbc7",
              |        "name": "data/objects/b16237456_0001.JP2",
              |        "path": "v3/data/objects/b16237456_0001.JP2",
              |        "size": 3931320,
              |        "type": "File"
              |      },
              |      {
              |        "checksum": "21b8887ad02ac0e28fb97105e895a0164c3cbf0db1d5dff5747eab2e30401c58",
              |        "name": "data/objects/b16237456_0002.JP2",
              |        "path": "v3/data/objects/b16237456_0002.JP2",
              |        "size": 3679235,
              |        "type": "File"
              |      }
              |    ],
              |    "type": "BagManifest"
              |  },
              |  "tagManifest": {
              |    "checksumAlgorithm": "SHA-256",
              |    "files": [
              |      {
              |        "checksum": "f0164324e54044613b756979343b4fd5a5a5b1ab222d26fca7b4f24679e2b19a",
              |        "name": "bag-info.txt",
              |        "path": "v3/bag-info.txt",
              |        "size": 295,
              |        "type": "File"
              |      }
              |    ],
              |    "type": "BagManifest"
              |  },
              |  "location": {
              |    "provider": {
              |      "id": "amazon-s3",
              |      "type": "Provider"
              |    },
              |    "bucket": "wellcomecollection-storage",
              |    "path": "digitised/b16237456",
              |    "type": "Location"
              |  },
              |  "replicaLocations": [
              |    {
              |      "provider": {
              |        "id": "amazon-s3",
              |        "type": "Provider"
              |      },
              |      "bucket": "wellcomecollection-storage-replica-ireland",
              |      "path": "digitised/b16237456",
              |      "type": "Location"
              |    }
              |  ],
              |  "createdDate": "2021-05-19T12:32:38.589051Z",
              |  "version": "v3",
              |  "type": "Bag"
              |}
              |""".stripMargin
          )
        )
      )
    )

    withBagRetriever(responses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b16237456"
          )

        whenReady(future) {
          bag =>
            bag.manifest.files.head shouldBe
              BagFile(
                name = "data/b16237456.xml",
                path = "v3/data/b16237456.xml"
              )

            bag.location.bucket shouldBe "wellcomecollection-storage"
            bag.location.path shouldBe "digitised/b16237456"

            bag.createdDate shouldBe Instant.parse(
              "2021-05-19T12:32:38.589051Z"
            )
        }
    }
  }

  it("fails if the bag does not exist in the storage service") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30246039")),
        HttpResponse(status = StatusCodes.NotFound)
      )
    )

    withBagRetriever(responses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30246039"
          )

        whenReady(future.failed) {
          _.getMessage shouldBe "Bag digitised/b30246039 does not exist in storage service"
        }
    }
  }

  it("does not retry if the storage service responds with unauthorized") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30246039")),
        HttpResponse(status = StatusCodes.Unauthorized)
      )
    )

    withBagRetriever(responses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30246039"
          )

        whenReady(future.failed) {
          _.getMessage should startWith(
            "Failed to authorize with storage service"
          )
        }
    }
  }

  it("returns a failed future if the storage service responds with 500") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30246039")),
        HttpResponse(status = StatusCodes.InternalServerError)
      )
    )

    withBagRetriever(responses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30246039"
          )

        whenReady(future.failed) {
          _.getMessage shouldBe "Received error from storage service: 500 Internal Server Error"
        }
    }
  }

  it("follows a 307 redirect to fetch the bag from S3") {
    val redirectUri = Uri(
      "https://wellcomecollection-storage-prod-large-response-cache.s3.eu-west-1.amazonaws.com/responses/digitised/b30414726/v1"
    )
    val bagJson =
      """
        |{
        |  "id": "digitised/b30414726",
        |  "space": {
        |    "id": "digitised",
        |    "type": "Space"
        |  },
        |  "info": {
        |    "externalIdentifier": "b30414726",
        |    "payloadOxum": "9999999999.999",
        |    "baggingDate": "2023-01-01",
        |    "sourceOrganization": "intranda GmbH",
        |    "externalDescription": "A very large bag",
        |    "internalSenderIdentifier": "1234",
        |    "internalSenderDescription": "large_bag_b30414726",
        |    "type": "BagInfo"
        |  },
        |  "manifest": {
        |    "checksumAlgorithm": "SHA-256",
        |    "files": [
        |      {
        |        "checksum": "abc123",
        |        "name": "data/b30414726.xml",
        |        "path": "v1/data/b30414726.xml",
        |        "size": 1000,
        |        "type": "File"
        |      }
        |    ],
        |    "type": "BagManifest"
        |  },
        |  "tagManifest": {
        |    "checksumAlgorithm": "SHA-256",
        |    "files": [],
        |    "type": "BagManifest"
        |  },
        |  "location": {
        |    "provider": {
        |      "id": "amazon-s3",
        |      "type": "Provider"
        |    },
        |    "bucket": "wellcomecollection-storage",
        |    "path": "digitised/b30414726",
        |    "type": "Location"
        |  },
        |  "replicaLocations": [],
        |  "createdDate": "2023-01-01T12:00:00.000000Z",
        |  "version": "v1",
        |  "type": "Bag"
        |}
        |""".stripMargin

    val storageResponses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30414726")),
        HttpResponse(
          status = StatusCodes.TemporaryRedirect,
          headers = List(Location(redirectUri))
        )
      )
    )

    val redirectResponses = Seq(
      (
        HttpRequest(uri = redirectUri),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            bagJson
          )
        )
      )
    )

    withBagRetriever(storageResponses, redirectResponses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30414726"
          )

        whenReady(future) {
          bag =>
            bag.location.bucket shouldBe "wellcomecollection-storage"
            bag.location.path shouldBe "digitised/b30414726"
            bag.manifest.files.head shouldBe BagFile(
              name = "data/b30414726.xml",
              path = "v1/data/b30414726.xml"
            )
        }
    }
  }

  it(
    "follows a 307 redirect and parses a bag served as application/octet-stream"
  ) {
    val redirectUri = Uri(
      "https://wellcomecollection-storage-prod-large-response-cache.s3.eu-west-1.amazonaws.com/responses/digitised/b30414727/v1"
    )
    val bagJson =
      """
        |{
        |  "id": "digitised/b30414727",
        |  "space": { "id": "digitised", "type": "Space" },
        |  "info": {
        |    "externalIdentifier": "b30414727",
        |    "payloadOxum": "1.1",
        |    "baggingDate": "2023-01-02",
        |    "sourceOrganization": "intranda GmbH",
        |    "externalDescription": "A very large bag with a wonky content type",
        |    "internalSenderIdentifier": "1235",
        |    "internalSenderDescription": "large_bag_b30414727",
        |    "type": "BagInfo"
        |  },
        |  "manifest": {
        |    "checksumAlgorithm": "SHA-256",
        |    "files": [
        |      {
        |        "checksum": "def456",
        |        "name": "data/b30414727.xml",
        |        "path": "v1/data/b30414727.xml",
        |        "size": 1000,
        |        "type": "File"
        |      }
        |    ],
        |    "type": "BagManifest"
        |  },
        |  "tagManifest": {
        |    "checksumAlgorithm": "SHA-256",
        |    "files": [],
        |    "type": "BagManifest"
        |  },
        |  "location": {
        |    "provider": { "id": "amazon-s3", "type": "Provider" },
        |    "bucket": "wellcomecollection-storage",
        |    "path": "digitised/b30414727",
        |    "type": "Location"
        |  },
        |  "replicaLocations": [],
        |  "createdDate": "2023-01-02T12:00:00.000000Z",
        |  "version": "v1",
        |  "type": "Bag"
        |}
        |""".stripMargin

    val storageResponses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30414727")),
        HttpResponse(
          status = StatusCodes.TemporaryRedirect,
          headers = List(Location(redirectUri))
        )
      )
    )

    val redirectResponses = Seq(
      (
        HttpRequest(uri = redirectUri),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/octet-stream`,
            bagJson.getBytes("UTF-8")
          )
        )
      )
    )

    withBagRetriever(storageResponses, redirectResponses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30414727"
          )

        whenReady(future) {
          bag =>
            bag.location.bucket shouldBe "wellcomecollection-storage"
            bag.location.path shouldBe "digitised/b30414727"
            bag.manifest.files.head shouldBe BagFile(
              name = "data/b30414727.xml",
              path = "v1/data/b30414727.xml"
            )
        }
    }
  }

  it("fails if a redirected bag response has an unexpected Content-Type") {
    val redirectUri = Uri(
      "https://wellcomecollection-storage-prod-large-response-cache.s3.eu-west-1.amazonaws.com/responses/digitised/b30414728/v1"
    )

    val storageResponses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30414728")),
        HttpResponse(
          status = StatusCodes.TemporaryRedirect,
          headers = List(Location(redirectUri))
        )
      )
    )

    val redirectResponses = Seq(
      (
        HttpRequest(uri = redirectUri),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`text/html(UTF-8)`,
            "<html>not a bag</html>"
          )
        )
      )
    )

    withBagRetriever(storageResponses, redirectResponses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30414728"
          )

        whenReady(future.failed) {
          _.getMessage should startWith(
            "Unexpected Content-Type from redirected bag response:"
          )
        }
    }
  }

  it("refuses to follow a 307 redirect to an unexpected URL") {
    val redirectUri = Uri("https://evil.example.com/steal-data")
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30414726")),
        HttpResponse(
          status = StatusCodes.TemporaryRedirect,
          headers = List(Location(redirectUri))
        )
      )
    )

    withBagRetriever(responses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30414726"
          )

        whenReady(future.failed) {
          _.getMessage should startWith(
            "Refusing to follow redirect to unexpected URL"
          )
        }
    }
  }

  it("fails if fetching the bag from S3 returns an error") {
    val redirectUri = Uri(
      "https://wellcomecollection-storage-prod-large-response-cache.s3.eu-west-1.amazonaws.com/responses/digitised/b30414726/v1?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc123secret"
    )
    val storageResponses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30414726")),
        HttpResponse(
          status = StatusCodes.TemporaryRedirect,
          headers = List(Location(redirectUri))
        )
      )
    )

    val redirectResponses = Seq(
      (
        HttpRequest(uri = redirectUri),
        HttpResponse(status = StatusCodes.Forbidden)
      )
    )

    withBagRetriever(storageResponses, redirectResponses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30414726"
          )

        whenReady(future.failed) {
          err =>
            val msg = err.getMessage
            msg shouldBe "Received error following redirect to https://wellcomecollection-storage-prod-large-response-cache.s3.eu-west-1.amazonaws.com/responses/digitised/b30414726/v1: 403 Forbidden"
            msg should not include "X-Amz-Signature"
            msg should not include "abc123secret"
        }
    }
  }

  it("fails if a 307 redirect has no Location header") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30414726")),
        HttpResponse(status = StatusCodes.TemporaryRedirect)
      )
    )

    withBagRetriever(responses) {
      retriever =>
        val future =
          retriever.getBag(
            space = "digitised",
            externalIdentifier = "b30414726"
          )

        whenReady(future.failed) {
          _.getMessage shouldBe "Received 307 redirect from storage service but no Location header"
        }
    }
  }

  def withBagRetriever[R](
    responses: Seq[(HttpRequest, HttpResponse)],
    redirectResponses: Seq[(HttpRequest, HttpResponse)] = Seq.empty
  )(testWith: TestWith[BagRetriever, R]): R =
    withActorSystem {
      implicit actorSystem =>
        val client = new MemoryHttpClient(responses) with HttpGet {
          override val baseUri: Uri = Uri("http://storage:1234/bags")
        }

        val redirectClient: HttpClient = new MemoryHttpClient(redirectResponses)

        testWith(new HttpBagRetriever(client, redirectClient))
    }
}
