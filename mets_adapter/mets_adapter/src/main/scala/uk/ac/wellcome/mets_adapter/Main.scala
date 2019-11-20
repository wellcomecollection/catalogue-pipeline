package uk.ac.wellcome.mets_adapter

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.scanamo.auto._

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.mets_adapter.services.{
  BagRetriever,
  HttpBagRetriever,
  MetsAdapterWorkerService,
  MetsStore,
  TokenService,
}
import uk.ac.wellcome.storage.store.dynamo.DynamoHashStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.storage.store.VersionedStore

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new MetsAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = ???),
      buildBagRetriever(config),
      buildMetsStore(config),
    )
  }

  private def buildBagRetriever(config: Config)(
    implicit
    actorSystem: ActorSystem,
    materializer: ActorMaterializer,
    ec: ExecutionContext): BagRetriever =
    new HttpBagRetriever(
      ???,
      buildTokenService(config)
    )

  private def buildTokenService(config: Config): TokenService =
    throw new NotImplementedError

  private def buildMetsStore(config: Config) = {
    implicit val dynamoClient = DynamoBuilder.buildDynamoClient(config)
    new MetsStore(
      new VersionedStore(
        new DynamoHashStore(
          DynamoBuilder.buildDynamoConfig(config)
        )
      )
    )
  }
}
