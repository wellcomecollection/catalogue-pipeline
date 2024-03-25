package weco.pipeline.mets_adapter.services

import akka.http.scaladsl.model._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.pipeline.mets_adapter.models._
import weco.http.client.{HttpGet, MemoryHttpClient}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class BagRetrieverTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Akka {

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

  def withBagRetriever[R](
    responses: Seq[(HttpRequest, HttpResponse)]
  )(testWith: TestWith[BagRetriever, R]): R =
    withActorSystem {
      implicit actorSystem =>
        val client = new MemoryHttpClient(responses) with HttpGet {
          override val baseUri: Uri = Uri("http://storage:1234/bags")
        }

        testWith(new HttpBagRetriever(client))
    }
}
