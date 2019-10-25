package uk.ac.wellcome.platform.transformer.mets.service

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.platform.transformer.mets.model.{Bag, BagFile, BagLocation, BagManifest}

class BagsRetrieverTest extends FunSpec with Matchers with BagsWiremock with Inside{
  it("gets a bag from the storage service"){
    withBagsService(8089, "localhost") {
      val bagsRetriever = new BagsRetriever("http://localhost:8089/storage/v1/bags")
      inside(bagsRetriever.getBag("digitised","b30246039")) { case Bag(_, BagManifest(files), BagLocation(bucket, path)) =>
        verify(getRequestedFor(urlEqualTo("/storage/v1/bags/digitised/b30246039")))
        files.head shouldBe BagFile("data/b30246039.xml", "v1/data/b30246039.xml")
        bucket shouldBe "wellcomecollection-storage"
          path shouldBe "digitised/b30246039"
      }
    }

  }

}
