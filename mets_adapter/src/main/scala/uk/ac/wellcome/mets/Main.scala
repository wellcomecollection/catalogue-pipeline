package uk.ac.wellcome.mets

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.amazonaws.services.sns.AmazonSNSAsync

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.typesafe.SQSBuilder

case class SNSConfig(topicArn: String)

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val sqsClient =
      SQSBuilder.buildSQSAsyncClient(config)
    implicit val snsClient =
      buildSNSClient(config)

    new MetsAdaptorWorkerService(
      SQSBuilder.buildSQSConfig(config),
      buildSNSConfig(config),
    )
  }

  private def buildSNSClient(config: Config): AmazonSNSAsync =
    throw new NotImplementedError

  private def buildSNSConfig(config: Config): SNSConfig =
    throw new NotImplementedError
}
