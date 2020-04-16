package uk.ac.wellcome.platform.inference_manager.fixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import scala.util.Random

trait InferrerStubs {
  val stubs: Seq[MappingBuilder]
}

object FeatureVectorInferrerMock extends InferrerStubs {
  val stubs = Seq(
    imageStub,
    iiifStub,
    imageWithStatusStub("extremely_cursed_image", 500),
    imageWithStatusStub("lost_image", 404),
    imageWithStatusStub("malformed_image_url", 400)
  )

  private lazy val imageStub =
    getRootStub.withQueryParam("image_url", matching(".+"))
  private lazy val iiifStub =
    getRootStub.withQueryParam("iiif_url", matching(".+"))

  private def imageWithStatusStub(imageUrl: String, status: Int) =
    get(urlPathEqualTo("/feature-vector/"))
      .withQueryParam("image_url", equalTo(imageUrl))
      .willReturn(aResponse().withStatus(status))

  private def getRootStub =
    get(urlPathEqualTo("/feature-vector/"))
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

  def randomFeatureVector: List[Float] = List.fill(4096)(Random.nextFloat)

  def randomLshVector: List[String] =
    List.fill(256)(s"${Random.nextInt(256)}-${Random.nextInt(32)}")
}

class InferrerWiremock(inferrer: InferrerStubs) {
  lazy private val server = {
    val _server = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .dynamicPort()
    )
    inferrer.stubs.foreach { stub =>
      _server.addStubMapping(stub.build())
    }
    _server
  }

  lazy val port: Int = server.port()

  def start(): Unit = {
    server.start()
    WireMock.configureFor("localhost", port)
  }

  def stop(): Unit = {
    server.stop()
  }
}
