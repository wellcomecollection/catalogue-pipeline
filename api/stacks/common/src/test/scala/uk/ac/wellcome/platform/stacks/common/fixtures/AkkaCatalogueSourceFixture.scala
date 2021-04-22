package uk.ac.wellcome.platform.stacks.common.fixtures

import akka.http.scaladsl.model.Uri
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.stacks.common.services.source.AkkaCatalogueSource

trait AkkaCatalogueSourceFixture extends CatalogueWireMockFixture with Akka {
  def withAkkaCatalogueSource[R](
    testWith: TestWith[AkkaCatalogueSource, R]
  ): R =
    withActorSystem { implicit actorSystem =>
      withMockCatalogueServer { baseUrl =>
        testWith(
          new AkkaCatalogueSource(baseUri = Uri(s"$baseUrl/catalogue/v2"))
        )
      }
    }
}
