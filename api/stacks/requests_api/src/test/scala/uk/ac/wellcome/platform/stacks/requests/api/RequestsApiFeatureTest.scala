package uk.ac.wellcome.platform.stacks.requests.api

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import com.github.tomakehurst.wiremock.client.WireMock.{
  equalToJson,
  postRequestedFor,
  urlEqualTo
}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.platform.stacks.requests.api.fixtures.RequestsApiFixture

class RequestsApiFeatureTest
    extends AnyFunSpec
    with Matchers
    with RequestsApiFixture
    with JsonAssertions
    with IntegrationPatience {

  describe("requests") {
    it("responds to requests containing an Weco-Sierra-Patron-Id header") {
      withApp { _ =>
        val path = "/requests"

        val headers = List(
          HttpHeader
            .parse(
              name = "Weco-Sierra-Patron-Id",
              value = "1234567"
            )
            .asInstanceOf[ParsingResult.Ok]
            .header
        )

        whenGetRequestReady(path, headers) { response =>
          response.status shouldBe StatusCodes.OK
        }
      }
    }

    it("accepts requests to place a hold on an item with a pickupDate") {
      withApp { wireMockServer =>
        val path = "/requests"

        val headers = List(
          HttpHeader
            .parse(
              name = "Weco-Sierra-Patron-Id",
              value = "1234567"
            )
            .asInstanceOf[ParsingResult.Ok]
            .header
        )

        val entity = createJsonHttpEntityWith(
          """
            |{
            |  "item": {
            |    "id": "ys3ern6x",
            |    "type": "Item"
            |  },
            |  "pickupDate": "2020-01-01T00:00:00Z",
            |  "type": "Request"
            |}
            |""".stripMargin
        )

        whenPostRequestReady(path, entity, headers) { response =>
          response.status shouldBe StatusCodes.Accepted

          wireMockServer.verify(
            1,
            postRequestedFor(
              urlEqualTo(
                "/iii/sierra-api/v5/patrons/1234567/holds/requests"
              )
            ).withRequestBody(
              equalToJson("""
                  |{
                  |  "recordType" : "i",
                  |  "recordNumber" : 1601017,
                  |  "pickupLocation" : "unspecified",
                  |  "neededBy" : "2020-01-01"
                  |}
                  |""".stripMargin)
            )
          )

          response.entity.isKnownEmpty() shouldBe true
        }
      }
    }

    it("accepts requests to place a hold on an item without a pickupDate") {
      withApp { wireMockServer =>
        val path = "/requests"

        val headers = List(
          HttpHeader
            .parse(
              name = "Weco-Sierra-Patron-Id",
              value = "1234567"
            )
            .asInstanceOf[ParsingResult.Ok]
            .header
        )

        val entity = createJsonHttpEntityWith(
          """
            |{
            |  "item": {
            |    "id": "ys3ern6x",
            |    "type": "Item"
            |  },
            |  "type": "Request"
            |}
            |""".stripMargin
        )

        whenPostRequestReady(path, entity, headers) { response =>
          response.status shouldBe StatusCodes.Accepted

          wireMockServer.verify(
            1,
            postRequestedFor(
              urlEqualTo(
                "/iii/sierra-api/v5/patrons/1234567/holds/requests"
              )
            ).withRequestBody(
              equalToJson("""
                  |{
                  |  "recordType" : "i",
                  |  "recordNumber" : 1601017,
                  |  "pickupLocation" : "unspecified"
                  |}
                  |""".stripMargin)
            )
          )

          response.entity.isKnownEmpty() shouldBe true
        }
      }
    }

    it("responds with a 409 Conflict when a hold is rejected") {
      withApp { wireMockServer =>
        val path = "/requests"

        val headers = List(
          HttpHeader
            .parse(
              name = "Weco-Sierra-Patron-Id",
              value = "1234567"
            )
            .asInstanceOf[ParsingResult.Ok]
            .header
        )

        val entity = createJsonHttpEntityWith(
          """
              |{
              |  "item": {
              |    "id": "ys3ern6y",
              |    "type": "Item"
              |  },
              |  "type": "Request"
              |}
              |""".stripMargin
        )

        whenPostRequestReady(path, entity, headers) { response =>
          response.status shouldBe StatusCodes.Conflict

          wireMockServer.verify(
            1,
            postRequestedFor(
              urlEqualTo(
                "/iii/sierra-api/v5/patrons/1234567/holds/requests"
              )
            ).withRequestBody(
              equalToJson("""
                    |{
                    |  "recordType" : "i",
                    |  "recordNumber" : 1601018,
                    |  "pickupLocation" : "unspecified"
                    |}
                    |""".stripMargin)
            )
          )

        }
      }
    }

    it("provides information about a users' holds") {
      withApp { _ =>
        val path = "/requests"

        val headers = List(
          HttpHeader
            .parse(
              name = "Weco-Sierra-Patron-Id",
              value = "1234567"
            )
            .asInstanceOf[ParsingResult.Ok]
            .header
        )

        val expectedJson =
          s"""
             |{
             |  "results" : [
             |    {
             |      "item" : {
             |        "id" : "n5v7b4md",
             |        "type" : "Item"
             |      },
             |      "pickupDate" : "2019-12-03T04:00:00Z",
             |      "pickupLocation" : {
             |        "id" : "sepbb",
             |        "label" : "Rare Materials Room",
             |        "type" : "LocationDescription"
             |      },
             |      "status" : {
             |        "id" : "i",
             |        "label" : "item hold ready for pickup.",
             |        "type" : "RequestStatus"
             |      },
             |      "type" : "Request"
             |    }
             |  ],
             |  "totalResults" : 1,
             |  "type" : "ResultList"
             |}""".stripMargin

        whenGetRequestReady(path, headers) { response =>
          response.status shouldBe StatusCodes.OK

          withStringEntity(response.entity) { actualJson =>
            assertJsonStringsAreEqual(actualJson, expectedJson)
          }
        }
      }
    }
  }
}
