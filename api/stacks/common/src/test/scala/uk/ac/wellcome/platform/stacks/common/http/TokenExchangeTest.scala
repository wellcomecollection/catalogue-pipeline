package uk.ac.wellcome.platform.stacks.common.http

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TokenExchangeTest extends AnyFunSpec with Matchers with ScalaFutures {
  type Credentials = String
  type Token = String

  class MemoryTokenExchange(
    tokens: Seq[(Credentials, Instant)]
  )(implicit val ec: ExecutionContext)
      extends TokenExchange[Credentials, Token] {
    var calls = 0
    override protected val expiryGracePeriod = 0

    override protected def getNewToken(
      credentials: Credentials): Future[(Token, Instant)] =
      synchronized {
        val index = calls
        calls += 1

        Future.fromTry(Try(tokens(index)))
      }
  }

  val credentials = "password"

  val now = Instant.now()

  it("retrieves a token") {
    val exchange = new MemoryTokenExchange(
      tokens = Seq(
        ("token1", now)
      )
    )

    whenReady(exchange.getToken(credentials)) {
      _ shouldBe "token1"
    }
  }

  it("caches a token, and doesn't fetch it again until it expires") {
    val exchange = new MemoryTokenExchange(
      tokens = Seq(
        ("token1", now.plusSeconds(1)),
        ("token2", now)
      )
    )

    // Get the token three times, verify we get the same token each time,
    // but that the underlying method was only called once.
    whenReady(exchange.getToken(credentials)) { resp1 =>
      resp1 shouldBe "token1"

      whenReady(exchange.getToken(credentials)) { resp2 =>
        resp2 shouldBe "token1"

        whenReady(exchange.getToken(credentials)) { resp3 =>
          resp3 shouldBe "token1"
        }
      }
    }

    exchange.calls shouldBe 1

    // Now wait for the existing token to expire, and check we can fetch the new token
    Thread.sleep(1500)

    whenReady(exchange.getToken(credentials)) {
      _ shouldBe "token2"
    }

    exchange.calls shouldBe 2
  }

  it("fails if there is no cached token and the underlying lookup fails") {
    val brokenExchange = new MemoryTokenExchange(tokens = Seq.empty)

    whenReady(brokenExchange.getToken(credentials).failed) {
      _ shouldBe a[IndexOutOfBoundsException]
    }
  }
}
