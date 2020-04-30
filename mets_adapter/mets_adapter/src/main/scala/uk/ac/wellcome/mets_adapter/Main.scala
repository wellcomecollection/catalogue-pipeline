package uk.ac.wellcome.mets_adapter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.auto._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.mets_adapter.services.{BagRetriever, HttpBagRetriever, MetsAdapterWorkerService, MetsStore, TokenService}
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val dynamoClilent: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    new MetsAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "METS adapter"),
      buildBagRetriever(config),
      buildMetsStore(config),
    )
  }

  private def buildMetsStore(config: Config)(
    implicit dynamoClilent: AmazonDynamoDB): MetsStore =
    new MetsStore(
      new DynamoSingleVersionStore(
        DynamoBuilder.buildDynamoConfig(config, namespace = "mets")
      )
    )

  private def buildBagRetriever(config: Config)(
    implicit
    actorSystem: ActorSystem,
    materializer: Materializer,
    ec: ExecutionContext): BagRetriever =
    new HttpBagRetriever(
      config.required[String]("bags.api.url"),
      new TokenService(
        config.required[String]("bags.oauth.url"),
        config.required[String]("bags.oauth.client_id"),
        config.required[String]("bags.oauth.secret"),
        config.required[String]("bags.api.url"),
        20 minutes
      )
    )
}
