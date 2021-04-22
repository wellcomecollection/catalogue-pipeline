package uk.ac.wellcome.platform.stacks.common.services.config.builders

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.Config
import uk.ac.wellcome.platform.stacks.common.services.SierraService
import uk.ac.wellcome.platform.stacks.common.services.source.AkkaSierraSource
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object SierraServiceBuilder {
  def build(config: Config)(
    implicit
    as: ActorSystem,
    ec: ExecutionContext
  ): SierraService = {
    val username = config.requireString("sierra.api.key")
    val password = config.requireString(("sierra.api.secret"))
    val baseUrl = config.requireString("sierra.api.baseUrl")

    new SierraService(
      new AkkaSierraSource(
        baseUri = Uri(baseUrl),
        credentials = BasicHttpCredentials(
          username = username,
          password = password
        )
      )
    )
  }
}
