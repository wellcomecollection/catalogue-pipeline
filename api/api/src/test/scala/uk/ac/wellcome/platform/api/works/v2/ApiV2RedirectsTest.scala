package uk.ac.wellcome.platform.api.works.v2

class ApiV2RedirectsTest extends ApiV2WorksTestBase {

  val redirectedWork = createIdentifiedRedirectedWork
  val redirectId = redirectedWork.redirect.canonicalId

  it("returns a TemporaryRedirect if looking up a redirected work") {
    withApi {
      case (indexV2, routes) => {
        insertIntoElasticsearch(indexV2, redirectedWork)
        val path = s"/$apiPrefix/works/${redirectedWork.canonicalId}"
        assertRedirectResponse(routes, path) {
          Status.Found ->
            s"$apiScheme://$apiHost/$apiPrefix/works/${redirectId}"
        }
      }
    }
  }

  it("preserves query parameters on a 302 Redirect") {
    withApi {
      case (indexV2, routes) => {
        insertIntoElasticsearch(indexV2, redirectedWork)
        val path =
          s"/$apiPrefix/works/${redirectedWork.canonicalId}?include=identifiers"
        assertRedirectResponse(routes, path) {
          Status.Found ->
            s"$apiScheme://$apiHost/$apiPrefix/works/${redirectId}?include=identifiers"
        }
      }
    }
  }
}
