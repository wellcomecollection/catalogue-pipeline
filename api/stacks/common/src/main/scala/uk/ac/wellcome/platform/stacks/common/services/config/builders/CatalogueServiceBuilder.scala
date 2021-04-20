package uk.ac.wellcome.platform.stacks.common.services.config.builders

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config
import uk.ac.wellcome.platform.stacks.common.services.CatalogueService
import uk.ac.wellcome.platform.stacks.common.services.source.AkkaCatalogueSource
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object CatalogueServiceBuilder {
  def build(config: Config)(
    implicit
    as: ActorSystem,
    ec: ExecutionContext
  ): CatalogueService = {
    val baseUrl = config.requireString("catalogue.api.baseUrl")

    new CatalogueService(
      new AkkaCatalogueSource(Uri(baseUrl))
    )
  }
}
