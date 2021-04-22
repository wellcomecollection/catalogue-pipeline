package uk.ac.wellcome.platform.stacks.common.fixtures

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.github.tomakehurst.wiremock.WireMockServer
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.stacks.common.services.source.AkkaSierraSource
import uk.ac.wellcome.platform.stacks.common.services.{
  CatalogueService,
  SierraService,
  StacksService
}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.ExecutionContext.Implicits.global

trait ServicesFixture
    extends AkkaCatalogueSourceFixture
    with SierraWireMockFixture {

  def withCatalogueService[R](
    testWith: TestWith[CatalogueService, R]
  ): R =
    withAkkaCatalogueSource { catalogueSource =>
      testWith(new CatalogueService(catalogueSource))
    }

  def withSierraService[R](
    testWith: TestWith[(SierraService, WireMockServer), R]
  ): R = {
    withMockSierraServer {
      case (sierraApiUrl, wireMockServer) =>
        withActorSystem { implicit as =>
          implicit val ec: ExecutionContextExecutor = as.dispatcher

          withMaterializer { _ =>
            testWith(
              (
                new SierraService(
                  new AkkaSierraSource(
                    baseUri = Uri(f"$sierraApiUrl/iii/sierra-api"),
                    credentials = BasicHttpCredentials("username", "password")
                  )
                ),
                wireMockServer
              )
            )
          }
        }
    }
  }

  def withStacksService[R](
    testWith: TestWith[(StacksService, WireMockServer), R]
  ): R = {
    withCatalogueService { catalogueService =>
      withSierraService {
        case (sierraService, sierraWireMockSerever) =>
          withActorSystem { implicit as =>
            implicit val ec: ExecutionContextExecutor = as.dispatcher

            val stacksService =
              new StacksService(catalogueService, sierraService)

            testWith(
              (stacksService, sierraWireMockSerever)
            )
          }
      }
    }
  }
}
