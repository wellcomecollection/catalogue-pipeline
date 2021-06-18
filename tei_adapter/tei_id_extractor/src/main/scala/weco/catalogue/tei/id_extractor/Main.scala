package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.tei.id_extractor.database.{
  PathIdTable,
  PathIdTableBuilder,
  RDSClientBuilder,
  TableProvisioner
}
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig.RichConfig
import weco.http.client.AkkaHttpClient
import weco.catalogue.tei.id_extractor.database.TableProvisioner

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)
    val rdsConfig = RDSClientBuilder.buildRDSClientConfig(config)
    val tableConfig = PathIdTableBuilder.buildTableConfig(config)
    val table = new PathIdTable(tableConfig)
    RDSClientBuilder.buildDB(rdsConfig)
    val messageSender =
      SNSBuilder.buildSNSMessageSender(config, subject = "TEI id extractor")
    val store = S3TypedStore[String]
    new TeiIdExtractorWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      gitHubBlobReader = new GitHubBlobContentReader(
        new AkkaHttpClient(),
        config.requireString("tei.github.token")),
      tableProvisioner = new TableProvisioner(rdsConfig, tableConfig),
      pathIdManager = new PathIdManager[SNSConfig](
        table,
        store,
        messageSender,
        bucket = config.requireString("tei.id_extractor.bucket")),
      config = TeiIdExtractorConfigBuilder.buildTeiIdExtractorConfig(config)
    )
  }
}
