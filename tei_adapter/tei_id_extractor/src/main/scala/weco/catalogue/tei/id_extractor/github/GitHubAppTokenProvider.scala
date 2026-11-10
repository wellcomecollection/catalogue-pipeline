package weco.catalogue.tei.id_extractor.github

import com.github.pjfanning.pekkohttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.generic.auto._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.{
  Accept,
  Authorization,
  OAuth2BearerToken
}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import weco.http.client.HttpClient

import java.nio.charset.StandardCharsets.UTF_8
import java.security.spec.{InvalidKeySpecException, PKCS8EncodedKeySpec}
import java.security.{KeyFactory, PrivateKey, Signature}
import java.time.{Clock, Instant}
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

case class InstallationToken(token: String, expires_at: Instant)

/** Mints GitHub App installation tokens, which expire after an hour, and caches
  * each one until shortly before it expires.
  */
class GitHubAppTokenProvider(
  underlying: HttpClient,
  appId: String,
  installationId: String,
  privateKeyPem: String,
  apiBaseUrl: String = "https://api.github.com",
  clock: Clock = Clock.systemUTC(),
  refreshMargin: FiniteDuration = 5.minutes
)(implicit ac: ActorSystem) {
  implicit val ec: ExecutionContext = ac.dispatcher

  private val privateKey = GitHubAppTokenProvider.parsePrivateKey(privateKeyPem)

  private val cached = new AtomicReference[Future[InstallationToken]](
    Future.failed(new NoSuchElementException("no token fetched yet"))
  )

  def token(): Future[String] = {
    val current = cached.get()
    current.value match {
      case Some(Success(t))
          if t.expires_at.isAfter(
            clock.instant().plusSeconds(refreshMargin.toSeconds)
          ) =>
        Future.successful(t.token)
      case None => current.map(_.token)
      case _ =>
        val next = Promise[InstallationToken]()
        if (cached.compareAndSet(current, next.future)) {
          next.completeWith(fetchToken())
          next.future.map(_.token)
        } else token()
    }
  }

  private def fetchToken(): Future[InstallationToken] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$apiBaseUrl/app/installations/$installationId/access_tokens",
      headers = List(
        Accept(
          MediaType.applicationWithFixedCharset(
            "vnd.github+json",
            HttpCharsets.`UTF-8`
          )
        ),
        Authorization(OAuth2BearerToken(appJwt()))
      )
    )
    underlying.singleRequest(request).flatMap {
      case HttpResponse(StatusCodes.Created, _, entity, _) =>
        Unmarshal(entity).to[InstallationToken]
      case HttpResponse(code, _, entity, _) =>
        entity.discardBytes()
        Future.failed(
          new RuntimeException(
            s"GitHub returned ${code.value} minting an installation token for app $appId"
          )
        )
    }
  }

  // GitHub caps App JWTs at 10 minutes; backdate iat to allow for clock skew.
  private def appJwt(): String = {
    val now = clock.instant().getEpochSecond
    val encoder = Base64.getUrlEncoder.withoutPadding()
    val header =
      encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".getBytes(UTF_8))
    val payload = encoder.encodeToString(
      s"""{"iat":${now - 60},"exp":${now + 540},"iss":"$appId"}""".getBytes(
        UTF_8
      )
    )
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initSign(privateKey)
    signer.update(s"$header.$payload".getBytes(UTF_8))
    s"$header.$payload.${encoder.encodeToString(signer.sign())}"
  }
}

object GitHubAppTokenProvider {
  // GitHub issues PKCS#1 keys, which the JDK can only load once wrapped as PKCS#8.
  def parsePrivateKey(pem: String): PrivateKey = {
    val der = Base64.getMimeDecoder.decode(
      pem.linesIterator.filterNot(_.startsWith("-----")).mkString
    )
    val keyFactory = KeyFactory.getInstance("RSA")
    try keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der))
    catch {
      case _: InvalidKeySpecException =>
        keyFactory.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1(der)))
    }
  }

  private def wrapPkcs1(pkcs1: Array[Byte]): Array[Byte] = {
    val version = Array[Byte](0x02, 0x01, 0x00)
    val rsaAlgorithm = Array[Byte](
      0x30,
      0x0d,
      0x06,
      0x09,
      0x2a,
      0x86.toByte,
      0x48,
      0x86.toByte,
      0xf7.toByte,
      0x0d,
      0x01,
      0x01,
      0x01,
      0x05,
      0x00
    )
    val octetString = derElement(0x04, pkcs1)
    derElement(0x30, version ++ rsaAlgorithm ++ octetString)
  }

  private def derElement(tag: Int, content: Array[Byte]): Array[Byte] = {
    val length =
      if (content.length < 0x80) Array(content.length.toByte)
      else {
        val bytes = BigInt(content.length).toByteArray.dropWhile(_ == 0)
        (0x80 | bytes.length).toByte +: bytes
      }
    (tag.toByte +: length) ++ content
  }
}
