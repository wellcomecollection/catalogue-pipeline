package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal.IdState
import uk.ac.wellcome.models.Implicits._

class WorksRedirectsTest extends ApiWorksTestBase {

  val redirectedWork = identifiedWork().redirected(
    IdState.Identified(
      canonicalId = createCanonicalId,
      sourceIdentifier = createSourceIdentifier
    )
  )
  val redirectId = redirectedWork.redirect.canonicalId

  it("returns a TemporaryRedirect if looking up a redirected work") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, redirectedWork)
        val path = s"/$apiPrefix/works/${redirectedWork.state.canonicalId}"
        assertRedirectResponse(routes, path) {
          Status.Found ->
            s"$apiScheme://$apiHost/$apiPrefix/works/$redirectId"
        }
    }
  }

  it("preserves query parameters on a 302 Redirect") {
    withApi {
      case (ElasticConfig(worksIndex, _), routes) =>
        insertIntoElasticsearch(worksIndex, redirectedWork)
        val path =
          s"/$apiPrefix/works/${redirectedWork.state.canonicalId}?include=identifiers"
        assertRedirectResponse(routes, path) {
          Status.Found ->
            s"$apiScheme://$apiHost/$apiPrefix/works/$redirectId?include=identifiers"
        }
    }
  }
}
