package uk.ac.wellcome.platform.stacks.common.http

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

trait TokenExchange[C, T] {
  private var cachedToken: Option[(T, Instant)] = None

  // How many seconds before the token expires should we go back and
  // fetch a new token?
  protected val expiryGracePeriod: Int = 60

  implicit val ec: ExecutionContext

  protected def getNewToken(credentials: C): Future[(T, Instant)]

  def getToken(credentials: C): Future[T] =
    cachedToken match {
      case Some((token, expiryTime))
          if expiryTime
            .minusSeconds(expiryGracePeriod)
            .isAfter(Instant.now()) =>
        Future.successful(token)

      case _ =>
        getNewToken(credentials).map {
          case (token, expiryTime) =>
            cachedToken = Some((token, expiryTime))
            token
        }
    }
}
