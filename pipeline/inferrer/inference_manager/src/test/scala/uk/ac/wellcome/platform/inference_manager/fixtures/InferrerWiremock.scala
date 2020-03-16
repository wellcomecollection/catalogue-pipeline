package uk.ac.wellcome.platform.inference_manager.fixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import uk.ac.wellcome.fixtures.TestWith

import scala.util.{Random, Try}

trait InferrerWiremockImplementation {
  val stubs: Seq[MappingBuilder]
}

object FeatureVectorInferrerMock extends InferrerWiremockImplementation {
  val stubs = Seq(imageStub, iiifStub)

  private def rootStub =
    get(urlPathEqualTo("/feature-vectors/"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(s"""{
          |  "features": [${randomFeatureVector.mkString(",")}],
          |  "lsh_encoded_features": [${randomLshVector
                         .map(str => s""""${str}"""")
                         .mkString(", ")}]
          |}""".stripMargin)
      )

  private lazy val imageStub =
    rootStub.withQueryParam("image_url", matching(".+"))
  private lazy val iiifStub =
    rootStub.withQueryParam("iiif_url", matching(".+"))

  def randomFeatureVector: List[Float] = List.fill(4096)(Random.nextFloat)

  def randomLshVector: List[String] =
    List.fill(256)(s"${Random.nextInt(256)}-${Random.nextInt(32)}")
}

trait InferrerWiremock {
  def withInferrerService[R](inferrer: InferrerWiremockImplementation)(
    testWith: TestWith[Int, R]): R = {
    val server = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .dynamicPort()
    )
    server.start()
    inferrer.stubs.foreach { stub =>
      server.addStubMapping(stub.build())
    }
    val port = server.port()
    WireMock.configureFor("localhost", port)
    val result = Try(testWith(port))
    server.stop()
    result.get
  }
}
