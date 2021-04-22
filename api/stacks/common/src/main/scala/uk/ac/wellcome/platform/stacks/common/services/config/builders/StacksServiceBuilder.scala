package uk.ac.wellcome.platform.stacks.common.services.config.builders

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.platform.stacks.common.services.StacksService

import scala.concurrent.ExecutionContext

object StacksServiceBuilder {
  def build(config: Config)(
    implicit
    as: ActorSystem,
    ec: ExecutionContext
  ): StacksService =
    new StacksService(
      catalogueService = CatalogueServiceBuilder.build(config),
      sierraService = SierraServiceBuilder.build(config)
    )
}
