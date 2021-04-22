package uk.ac.wellcome.platform.stacks.common.services.source
import org.scalatest.concurrent.IntegrationPatience
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.stacks.common.fixtures.AkkaCatalogueSourceFixture

class AkkaCatalogueSourceTest
    extends CatalogueSourceTestCases[AkkaCatalogueSource]
    with AkkaCatalogueSourceFixture
    with IntegrationPatience {
  override def withCatalogueSource[R](
    testWith: TestWith[AkkaCatalogueSource, R]
  ): R =
    withAkkaCatalogueSource { akkaCatalogueSource =>
      testWith(akkaCatalogueSource)
    }
}
