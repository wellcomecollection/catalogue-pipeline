package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.tei.id_extractor.database.TableProvisioner

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
 val messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "TEI id extractor")
    val  store = S3TypedStore[String](???, ???)
    new TeiIdExtractorWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      gitHubBlobReader = new GitHubBlobReader(),
      tableProvisioner = new TableProvisioner(???)(???, ???),
      pathIdManager = new PathIdManager[SNSConfig](???, store, messageSender, bucket = ???),
      config = TeiIdExtractorConfig(concurrentFiles = ???)
    )
  }
}
