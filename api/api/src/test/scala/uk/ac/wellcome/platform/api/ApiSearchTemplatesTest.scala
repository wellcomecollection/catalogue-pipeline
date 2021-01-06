package uk.ac.wellcome.platform.api

import akka.http.scaladsl.model.ContentTypes
import io.circe.Json
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.api.works.ApiWorksTestBase

class ApiSearchTemplatesTest
    extends ApiWorksTestBase
    with Matchers
    with JsonHelpers {

  it("renders a list of available work search templates") {
    checkWorkJson { json =>
      json.isObject shouldBe true
      val templates = getKey(json, "templates")
      templates should not be empty
      templates map { json =>
        getLength(json).get should be > 0
      }

    }
  }

  it("renders a list of available image search templates") {
    checkImageJson { json =>
      json.isObject shouldBe true
      val templates = getKey(json, "templates")
      templates should not be empty
      templates map { json =>
        getLength(json).get should be > 0
      }
    }
  }

  private def checkWorkJson(f: Json => Unit): Unit =
    withApi { routes =>
      Get(s"/$apiPrefix/work-search-templates.json") ~> routes ~> check {
        status shouldEqual Status.OK
        contentType shouldEqual ContentTypes.`application/json`
        f(parseJson(responseAs[String]))
      }
    }

  private def checkImageJson(f: Json => Unit): Unit =
    withApi { routes =>
      Get(s"/$apiPrefix/image-search-templates.json") ~> routes ~> check {
        status shouldEqual Status.OK
        contentType shouldEqual ContentTypes.`application/json`
        f(parseJson(responseAs[String]))
      }
    }
}
