package weco.catalogue.tei.id_extractor.github

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.Authorization
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.http.client.HttpClient
import weco.pekko.fixtures.Pekko

import java.nio.charset.StandardCharsets.UTF_8
import java.security.{KeyPairGenerator, Signature}
import java.time.{Clock, Instant, ZoneOffset}
import java.util.Base64
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class GitHubAppTokenProviderTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Pekko
    with IntegrationPatience {

  private val keyPair = {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    generator.generateKeyPair()
  }

  // Strip the PKCS#8 wrapper to get the PKCS#1 form GitHub hands out; armour lines are ignored.
  private val pkcs1Pem =
    Base64.getMimeEncoder.encodeToString(keyPair.getPrivate.getEncoded.drop(26))

  private val now = Instant.parse("2026-11-06T14:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  class RecordingHttpClient(responses: HttpResponse*) extends HttpClient {
    val requests: ListBuffer[HttpRequest] = ListBuffer()
    private val remaining = responses.iterator
    override def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      requests += request
      Future.successful(remaining.next())
    }
  }

  private def tokenResponse(token: String, expiresAt: Instant) =
    HttpResponse(
      status = StatusCodes.Created,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""{"token":"$token","expires_at":"$expiresAt"}"""
      )
    )

  private def bearer(request: HttpRequest): String =
    request.header[Authorization].get.credentials.token()

  it(
    "signs an app JWT with the PKCS#1 key and returns the installation token"
  ) {
    val client = new RecordingHttpClient(
      tokenResponse("ghs_one", now.plusSeconds(3600))
    )
    withActorSystem {
      implicit ac =>
        val provider = new GitHubAppTokenProvider(
          client,
          "123",
          "456",
          pkcs1Pem,
          clock = clock
        )

        whenReady(provider.token()) { _ shouldBe "ghs_one" }

        val request = client.requests.head
        request.method shouldBe HttpMethods.POST
        request.uri.toString shouldBe "https://api.github.com/app/installations/456/access_tokens"

        val Array(header, payload, signature) = bearer(request).split('.')
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keyPair.getPublic)
        verifier.update(s"$header.$payload".getBytes(UTF_8))
        verifier.verify(Base64.getUrlDecoder.decode(signature)) shouldBe true

        new String(Base64.getUrlDecoder.decode(payload), UTF_8) shouldBe
          s"""{"iat":${now.getEpochSecond - 60},"exp":${now.getEpochSecond + 540},"iss":"123"}"""
    }
  }

  it("reuses a token until it is close to expiry") {
    val client = new RecordingHttpClient(
      tokenResponse("ghs_one", now.plusSeconds(3600)),
      tokenResponse("ghs_two", now.plusSeconds(3600))
    )
    withActorSystem {
      implicit ac =>
        val provider = new GitHubAppTokenProvider(
          client,
          "123",
          "456",
          pkcs1Pem,
          clock = clock
        )
        whenReady(provider.token()) { _ shouldBe "ghs_one" }
        whenReady(provider.token()) { _ shouldBe "ghs_one" }
        client.requests should have size 1
    }
  }

  it("fetches a new token when the cached one is about to expire") {
    val client = new RecordingHttpClient(
      tokenResponse("ghs_one", now.plusSeconds(60)),
      tokenResponse("ghs_two", now.plusSeconds(3600))
    )
    withActorSystem {
      implicit ac =>
        val provider = new GitHubAppTokenProvider(
          client,
          "123",
          "456",
          pkcs1Pem,
          clock = clock
        )
        whenReady(provider.token()) { _ shouldBe "ghs_one" }
        whenReady(provider.token()) { _ shouldBe "ghs_two" }
    }
  }

  it("retries after a failed fetch") {
    val client = new RecordingHttpClient(
      HttpResponse(status = StatusCodes.Unauthorized),
      tokenResponse("ghs_two", now.plusSeconds(3600))
    )
    withActorSystem {
      implicit ac =>
        val provider = new GitHubAppTokenProvider(
          client,
          "123",
          "456",
          pkcs1Pem,
          clock = clock
        )
        whenReady(provider.token().failed) {
          _.getMessage should include("401")
        }
        whenReady(provider.token()) { _ shouldBe "ghs_two" }
    }
  }

  it("accepts a PKCS#8 key as well as PKCS#1") {
    val pkcs8 =
      Base64.getMimeEncoder.encodeToString(keyPair.getPrivate.getEncoded)
    GitHubAppTokenProvider.parsePrivateKey(pkcs8) shouldBe keyPair.getPrivate
    GitHubAppTokenProvider.parsePrivateKey(pkcs1Pem) shouldBe keyPair.getPrivate
  }
}
