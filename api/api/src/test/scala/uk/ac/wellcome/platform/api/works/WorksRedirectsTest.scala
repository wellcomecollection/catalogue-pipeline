package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.elasticsearch.ElasticConfig

class WorksRedirectsTest extends ApiWorksTestBase {

  val redirectedWork = createIdentifiedRedirectedWork
  val redirectId = redirectedWork.redirect.canonicalId

  it("returns a TemporaryRedirect if looking up a redirected work") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) => {
        insertIntoElasticsearch(worksIndex, redirectedWork)
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
      case (ElasticConfig(worksIndex, _), routes) => {
        insertIntoElasticsearch(worksIndex, redirectedWork)
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
