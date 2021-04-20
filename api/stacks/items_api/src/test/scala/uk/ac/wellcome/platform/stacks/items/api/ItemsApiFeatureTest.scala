package uk.ac.wellcome.platform.stacks.items.api

import akka.http.scaladsl.model.StatusCodes
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.platform.stacks.items.api.fixtures.ItemsApiFixture

class ItemsApiFeatureTest
    extends AnyFunSpec
    with Matchers
    with ItemsApiFixture
    with JsonAssertions
    with IntegrationPatience {

  describe("items") {
    it("shows a user the items on a work") {
      withApp { _ =>
        val path = "/works/cnkv77md"

        val expectedJson =
          s"""
             |{
             |  "id" : "cnkv77md",
             |  "items" : [
             |    {
             |      "id" : "ys3ern6x",
             |      "status" : {
             |        "id" : "available",
             |        "label" : "Available",
             |        "type": "ItemStatus"
             |      },
             |      "type": "Item"
             |    }
             |  ],
             |  "type": "Work"
             |}""".stripMargin

        whenGetRequestReady(path) { response =>
          response.status shouldBe StatusCodes.OK

          withStringEntity(response.entity) { actualJson =>
            assertJsonStringsAreEqual(actualJson, expectedJson)
          }
        }
      }
    }
  }
}
