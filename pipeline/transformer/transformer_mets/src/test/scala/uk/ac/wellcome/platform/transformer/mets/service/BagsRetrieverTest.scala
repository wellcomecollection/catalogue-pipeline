package uk.ac.wellcome.platform.transformer.mets.service

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.transformer.mets.model.{Bag, BagFile, BagLocation, BagManifest}

import scala.concurrent.ExecutionContext.Implicits.global

class BagsRetrieverTest extends FunSpec with Matchers with BagsWiremock with Inside with ScalaFutures with Akka with IntegrationPatience {

  it("gets a bag from the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>

          val bagsRetriever = new BagsRetriever("http://localhost:8089/storage/v1/bags")
          whenReady(bagsRetriever.getBag("digitised", "b30246039")) { maybeBag =>
            inside(maybeBag) { case Some(Bag(_, BagManifest(files), BagLocation(bucket, path))) =>
              verify(getRequestedFor(urlEqualTo("/storage/v1/bags/digitised/b30246039")))
              files.head shouldBe BagFile("data/b30246039.xml", "v1/data/b30246039.xml")
              bucket shouldBe "wellcomecollection-storage"
              path shouldBe "digitised/b30246039"
            }
          }
        }
      }
    }
  }

  it("returns a none if the bag does not exist in the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val bagsRetriever = new BagsRetriever("http://localhost:8089/storage/v1/bags")
          whenReady(bagsRetriever.getBag("digitised", "not-existing")) { maybeBag =>
            maybeBag shouldBe None
          }
        }
      }
    }
  }

  it("returns a failed future if the storage service responds with 500") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          stubFor(get(urlMatching("/storage/v1/bags/digitised/this-shall-crash")).willReturn(aResponse().withStatus(500)))

          val bagsRetriever = new BagsRetriever("http://localhost:8089/storage/v1/bags")

          whenReady(bagsRetriever.getBag("digitised", "this-shall-crash").failed) { e =>
            e shouldBe a[Throwable]
          }
        }
      }
    }
  }

  it("returns a failed future if the storage service response has a fault") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          stubFor(get(urlMatching("/storage/v1/bags/digitised/this-will-fault")).willReturn(aResponse().withStatus(200).withFault(Fault.CONNECTION_RESET_BY_PEER)))
          val bagsRetriever = new BagsRetriever("http://localhost:8089/storage/v1/bags")

          whenReady(bagsRetriever.getBag("digitised", "this-will-fault").failed) { e =>
            e shouldBe a[Throwable]
          }
        }

      }
    }
  }
}
