package uk.ac.wellcome.platform.api

import uk.ac.wellcome.platform.api.works.ApiWorksTestBase

class ApiErrorsTest extends ApiWorksTestBase {

  it("returns a Not Found error if you try to get a non-existent API version") {
    withApi {
      case (_, routes) =>
        assertJsonResponse(routes, "/catalogue/v567/works") {
          Status.NotFound -> notFound(
            getApiPrefix(),
            "Page not found for URL /catalogue/v567/works"
          )
        }
    }
  }

  it("returns a Not Found error if you try to get an unrecognised path") {
    withApi {
      case (_, routes) =>
        assertJsonResponse(routes, "/foo/bar") {
          Status.NotFound -> notFound(
            getApiPrefix(),
            "Page not found for URL /foo/bar"
          )
        }
    }
  }
}
