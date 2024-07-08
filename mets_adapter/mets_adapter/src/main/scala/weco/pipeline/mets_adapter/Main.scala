package weco.pipeline.mets_adapter

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.Config
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.catalogue.source_model.mets.MetsSourceData
import weco.http.client.AkkaHttpClient
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.mets_adapter.http.StorageServiceOauthHttpClient
import weco.pipeline.mets_adapter.services.{
  HttpBagRetriever,
  MetsAdapterWorkerService
}
import weco.storage.store.dynamo.DynamoSingleVersionStore
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      implicit val dynamoClient: DynamoDbClient =
        DynamoDbClient.builder().build()

      val oauthClient = new StorageServiceOauthHttpClient(
        underlying = new AkkaHttpClient(),
        baseUri = Uri(config.requireString("bags.api.url")),
        tokenUri = Uri(config.requireString("bags.oauth.url")),
        credentials = BasicHttpCredentials(
          config.requireString("bags.oauth.client_id"),
          config.requireString("bags.oauth.secret")
        )
      )
      val metsStore = new DynamoSingleVersionStore[String, MetsSourceData](
        DynamoBuilder.buildDynamoConfig(config, namespace = "mets")
      )

      new MetsAdapterWorkerService(
        SQSBuilder.buildSQSStream(config),
        SNSBuilder.buildSNSMessageSender(config, subject = "METS adapter"),
        bagRetriever = new HttpBagRetriever(oauthClient),
        metsStore = metsStore
      )
  }
}
