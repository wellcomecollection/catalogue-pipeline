package uk.ac.wellcome.platform.api.works

class ApiErrorsTest extends ApiWorksTestBase {

  it("returns a Not Found error if you try to get an API version") {
    withApi {
      case (index, routes) =>
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
      case (index, routes) =>
        assertJsonResponse(routes, "/foo/bar") {
          Status.NotFound -> notFound(
            getApiPrefix(),
            "Page not found for URL /foo/bar"
          )
        }
    }
  }
}
