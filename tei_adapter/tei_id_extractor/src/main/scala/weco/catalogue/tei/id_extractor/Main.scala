package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig.RichConfig

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    new TeiIdExtractorWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "TEI id extractor"),
      gitHubBlobReader = new GitHubBlobReader(config.requireString("tei.github.token")),
      store = S3TypedStore[String](???, ???),
      config = TeiIdExtractorConfig(???,???)
    )
  }
}
