package uk.ac.wellcome.platform.api.works

import scala.io.Source
import akka.http.scaladsl.model.StatusCodes._

import uk.ac.wellcome.display.models.ApiVersions

class ApiContextTest extends ApiWorksTestBase {

  it("returns a context for v2") {
    withApi {
      case (index, routes) =>
        val path = s"/${getApiPrefix(ApiVersions.v2)}/context.json"
        assertJsonResponse(routes, path)(
          OK ->
            Source.fromResource("context-v2.json").getLines.mkString("\n")
        )
    }
  }
}
