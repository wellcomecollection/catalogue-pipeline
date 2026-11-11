package weco.catalogue.tei.id_extractor

import org.apache.pekko.actor.ActorSystem
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
import weco.http.client.PekkoHttpClient
import weco.catalogue.tei.id_extractor.github.{
  GitHubAppTokenProvider,
  GitHubAuthenticatedHttpClient
}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config =>
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

      val underlyingHttpClient = new PekkoHttpClient()
      val tokenProvider = new GitHubAppTokenProvider(
        underlying = underlyingHttpClient,
        appId = config.requireString("tei.github.app.id"),
        installationId = config.requireString("tei.github.app.installation_id"),
        privateKeyPem = config.requireString("tei.github.app.private_key")
      )
      val httpClient = new GitHubAuthenticatedHttpClient(
        underlying = underlyingHttpClient,
        token = () => tokenProvider.token()
      )

      new TeiIdExtractorWorkerService(
        messageStream = SQSBuilder.buildSQSStream(config),
        gitHubBlobReader = new GitHubBlobContentReader(httpClient),
        tableProvisioner = new TableProvisioner(rdsConfig, tableConfig),
        pathIdManager = new PathIdManager[SNSConfig](
          table,
          store,
          messageSender,
          bucket = config.requireString("tei.id_extractor.bucket")
        ),
        config = TeiIdExtractorConfigBuilder.buildTeiIdExtractorConfig(config)
      )
  }
}
