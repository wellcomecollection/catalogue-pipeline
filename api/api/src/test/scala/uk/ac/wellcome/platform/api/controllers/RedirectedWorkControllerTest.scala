package uk.ac.wellcome.platform.api.controllers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.IdentifiedRedirectedWork

class RedirectedWorkControllerTest
    extends FunSpec
    with Matchers
    with WorksGenerators {
  val canonicalId: String = createCanonicalId

  val controller: RedirectedWorkController = new RedirectedWorkController {}

  val apiScheme = "https"
  val apiHost = "api.wellcomecollection.org"

  val redirectedWork: IdentifiedRedirectedWork =
    createIdentifiedRedirectedWorkWith(
      canonicalId = canonicalId
    )

  it("creates a redirect URI") {
    val redirectURI = createRedirectResponseUri(
      s"http://localhost:8888/catalogue/v2/works/$canonicalId"
    )

    redirectURI shouldBe s"$apiScheme://$apiHost/catalogue/v2/works/${redirectedWork.redirect.canonicalId}"
  }

  it("preserves API query parameters") {
    val redirectURI = createRedirectResponseUri(
      s"http://localhost:8888/catalogue/v2/works/$canonicalId?query=hello%20world"
    )

    redirectURI should endWith("?query=hello%20world")
  }

  private def createRedirectResponseUri(uri: String): String = {
    val controller = new RedirectedWorkController {}

    controller
      .createRedirectResponseURI(
        originalUri = uri,
        work = redirectedWork,
        apiScheme = apiScheme,
        apiHost = apiHost
      )
      .toString
  }
}
