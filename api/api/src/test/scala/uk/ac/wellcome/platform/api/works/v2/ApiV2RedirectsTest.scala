package uk.ac.wellcome.platform.api.works.v2

import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer

class ApiV2RedirectsTest extends ApiV2WorksTestBase {
  it("returns a TemporaryRedirect if looking up a redirected work") {
    val redirectedWork = createIdentifiedRedirectedWork

    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        insertIntoElasticsearch(indexV2, redirectedWork)
        server.httpGet(
          path = s"/$apiPrefix/works/${redirectedWork.canonicalId}",
          andExpect = Status.Found,
          withBody = "",
          withLocation =
            s"/$apiPrefix/works/${redirectedWork.redirect.canonicalId}"
        )
    }
  }

  it("uses the configured host/scheme for the Location header") {
    val redirectedWork = createIdentifiedRedirectedWork

    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        insertIntoElasticsearch(indexV2, redirectedWork)
        val resp = server.httpGet(
          path = s"/$apiPrefix/works/${redirectedWork.canonicalId}",
          andExpect = Status.Found
        )

        resp.headerMap.getOrNull("Location") should startWith(s"$apiScheme://$apiHost")
    }
  }

  it("preserves query parameters on a 302 Redirect") {
    val redirectedWork = createIdentifiedRedirectedWork

    withV2Api {
      case (indexV2, server: EmbeddedHttpServer) =>
        insertIntoElasticsearch(indexV2, redirectedWork)
        server.httpGet(
          path =
            s"/$apiPrefix/works/${redirectedWork.canonicalId}?include=identifiers",
          andExpect = Status.Found,
          withBody = "",
          withLocation =
            s"/$apiPrefix/works/${redirectedWork.redirect.canonicalId}?include=identifiers"
        )
    }
  }
}
