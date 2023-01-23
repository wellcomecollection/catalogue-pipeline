package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import software.amazon.awssdk.services.s3.S3Client
import weco.messaging.sns.SNSConfig
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp
import weco.catalogue.tei.id_extractor.database.{
  PathIdTable,
  PathIdTableBuilder,
  RDSClientBuilder,
  TableProvisioner
}
import weco.typesafe.config.builders.EnrichConfig.RichConfig
import weco.http.client.AkkaHttpClient
import weco.catalogue.tei.id_extractor.github.GitHubAuthenticatedHttpClient

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")

    implicit val ec: ExecutionContext =
      actorSystem.dispatcher

    implicit val s3Client: S3Client = S3Client.builder().build()

    val rdsConfig = RDSClientBuilder.buildRDSClientConfig(config)
    val tableConfig = PathIdTableBuilder.buildTableConfig(config)
    val table = new PathIdTable(tableConfig)
    RDSClientBuilder.buildDB(rdsConfig)
    val messageSender =
      SNSBuilder.buildSNSMessageSender(config, subject = "TEI id extractor")
    val store = S3TypedStore[String]

    val httpClient = new GitHubAuthenticatedHttpClient(
      underlying = new AkkaHttpClient(),
      token = config.requireString("tei.github.token")
    )

    new TeiIdExtractorWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      gitHubBlobReader = new GitHubBlobContentReader(httpClient),
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
