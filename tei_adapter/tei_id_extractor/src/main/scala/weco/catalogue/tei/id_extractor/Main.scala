package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    new TeiIdExtractorWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "TEI id extractor"),
      gitHubBlobReader = new GitHubBlobReader(),
      concurrentFiles = ???,
      idExtractor = new IdExtractor(),
      store = S3TypedStore[String](???, ???),
      bucket = ???
    )
  }
}
