package uk.ac.wellcome.mets_adapter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.Config
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.mets_adapter.services.{
  HttpBagRetriever,
  MetsAdapterWorkerService,
  MetsStore
}
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import weco.catalogue.mets_adapter.http.StorageServiceOauthHttpClient
import weco.http.client.{AkkaHttpClient, HttpGet, HttpPost}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val dynamoClilent: DynamoDbClient =
      DynamoBuilder.buildDynamoClient(config)

    val client = new AkkaHttpClient() with HttpGet with HttpPost {
      override val baseUri: Uri = Uri(config.requireString("bags.api.url"))
    }

    val oauthClient = new StorageServiceOauthHttpClient(
      underlying = client,
      tokenUri = Uri(config.requireString("bags.oauth.url")),
      credentials = BasicHttpCredentials(
        config.requireString("bags.oauth.client_id"),
        config.requireString("bags.oauth.secret"),
      )
    )

    new MetsAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "METS adapter"),
      bagRetriever = new HttpBagRetriever(oauthClient),
      buildMetsStore(config),
    )
  }

  private def buildMetsStore(config: Config)(
    implicit dynamoClient: DynamoDbClient): MetsStore =
    new MetsStore(
      new DynamoSingleVersionStore(
        DynamoBuilder.buildDynamoConfig(config, namespace = "mets")
      )
    )
}
