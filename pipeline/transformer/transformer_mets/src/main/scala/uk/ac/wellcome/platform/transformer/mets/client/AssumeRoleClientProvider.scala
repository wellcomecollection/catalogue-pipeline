package uk.ac.wellcome.platform.transformer.mets.client

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

class AssumeRoleClientProvider[T](
  stsClient: AWSSecurityTokenService,
  roleArn: String,
  interval: FiniteDuration)(clientFactory: ClientFactory[T])(
  implicit actorSystem: ActorSystem,
  ec: ExecutionContext) {
  private val client = new AtomicReference[T]

  actorSystem.scheduler.scheduleWithFixedDelay(0 milliseconds, interval)(() =>
    refreshClient())

  def getClient: Either[Throwable, T] = {
    Option(client.get()) match {
      case Some(client) => Right(client)
      case None         => refreshClient()
    }
  }

  private def refreshClient() =
    Try {
      val assumeRoleResult = stsClient.assumeRole(
        new AssumeRoleRequest()
          .withRoleArn(roleArn)
          .withRoleSessionName("transformer"))
      val temporaryCredentials = new BasicSessionCredentials(
        assumeRoleResult.getCredentials.getAccessKeyId,
        assumeRoleResult.getCredentials.getSecretAccessKey,
        assumeRoleResult.getCredentials.getSessionToken);

      val t = clientFactory.buildClient(temporaryCredentials)
      client.set(t)
      t
    }.toEither
}
